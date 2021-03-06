package io.takari.jdkget.it;

import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.junit.Test;

import io.takari.jdkget.Arch;
import io.takari.jdkget.IOutput;
import io.takari.jdkget.JdkGetter;
import io.takari.jdkget.JdkReleases;
import io.takari.jdkget.JdkReleases.JdkRelease;
import io.takari.jdkget.JdkVersion;
import io.takari.jdkget.OracleWebsiteTransport;

public class LoadAllIT {

  @Test
  public void testDownloadAndUnpackAll() throws IOException {

    boolean[] failed = new boolean[] {false};

    JdkReleases releases = JdkReleases.get();
    String startWith = System.getProperty("io.takari.jdkget.startWith");
    String otnu = System.getProperty("io.takari.jdkget.otn.username");
    String otnp = System.getProperty("io.takari.jdkget.otn.password");

    JdkVersion start = null;
    if(StringUtils.isNotBlank(startWith)) {
      start = releases.select(JdkVersion.parse(startWith)).getVersion();
    }

    for (JdkRelease r : releases.getReleases()) {
      if(start != null && r.getVersion().compareTo(start) > 0) {
        continue;
      }

      JdkVersion v = r.getVersion();
      System.out.println(v.longBuild() + " / " + v.shortBuild() + (r.isPsu() ? " PSU" : ""));
      Stream<Arch> a = r.getArchs().parallelStream();

      a.forEach(arch -> {
        File jdktmp = new File("target/tmp/" + arch);
        O o = new O();
        try {
          if (jdktmp.exists()) {
            FileUtils.forceDelete(jdktmp);
          }
          FileUtils.forceMkdir(jdktmp);

          JdkGetter.builder()
            .transport(new OracleWebsiteTransport(OracleWebsiteTransport.ORACLE_WEBSITE, otnu, otnp))
            .version(r.getVersion())
            .unrestrictedJCE()
            .arch(arch)
            .outputDirectory(jdktmp)
            .output(o)
            .build().get();

          System.out.println("  " + arch + " >> OK");
          try (PrintStream out = new PrintStream(new File("target/tmp/" + v.toString() + "_" + arch + ".log"))) {
            o.output(out);
          }
        } catch (Exception e) {
          failed[0] = true;
          System.err.println("  " + arch + " >> FAIL");
          o.output(System.err);
          e.printStackTrace();
        }
        try {
          FileUtils.forceDelete(jdktmp);
        } catch (IOException e) {
          e.printStackTrace();
        }
      });

      System.gc();
      Runtime rt = Runtime.getRuntime();
      long total = rt.totalMemory();
      long free = rt.freeMemory();
      long occ = total - free;
      System.out.println("    MEM: " + (occ / 1024L / 1024L));
    }

    assertFalse(failed[0]);
  }

  private static class O implements IOutput {

    private List<String> msgs = new ArrayList<>();

    @Override
    public void info(String message) {
      msgs.add("[INFO] " + message);

    }

    @Override
    public void error(String message) {
      msgs.add("[ERROR] " + message);
    }

    @Override
    public void error(String message, Throwable t) {
      error(message + ": " + t);
    }

    public void output(PrintStream out) {
      for (String msg : msgs) {
        out.println(msg);
      }
    }

  }
}
