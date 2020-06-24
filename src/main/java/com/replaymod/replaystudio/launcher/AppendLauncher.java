package com.replaymod.replaystudio.launcher;

import com.google.gson.JsonObject;
import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.Studio;
import com.replaymod.replaystudio.filter.StreamFilter;
import com.replaymod.replaystudio.io.ReplayOutputStream;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import com.replaymod.replaystudio.replay.ZipReplayFile;
import com.replaymod.replaystudio.stream.PacketStream;
import com.replaymod.replaystudio.studio.ReplayStudio;
import com.replaymod.replaystudio.us.myles.ViaVersion.api.protocol.ProtocolVersion;
import com.replaymod.replaystudio.us.myles.ViaVersion.packets.State;
import org.apache.commons.cli.CommandLine;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static java.lang.System.in;

public class AppendLauncher {
  public void launch(CommandLine cmd) throws Exception{
    //Initialize list of ReplyFiles by reading in all but last paths
    List <ReplayFile> inFiles = new ArrayList<>();
    for(int i = 0; i < cmd.getArgs().length - 1; i++){
      inFiles.add(new ZipReplayFile(new ReplayStudio(), new File(cmd.getArgs()[i])));
    }


    ReplayMetaData meta = inFiles.get(0).getMetaData();
    ProtocolVersion inputVersion = meta.getProtocolVersion();

    //Set up the output stream for writing final file
    ReplayOutputStream out;
    String output = cmd.getArgs()[cmd.getArgs().length - 1];
    if (!"x".equals(output)) {
      OutputStream buffOut = new BufferedOutputStream(new FileOutputStream(output));
      out = new ReplayOutputStream(inputVersion, buffOut, null);
    } else {
      out = null;
    }
    long timeOffset = 0;
    for(int i = 0; i < inFiles.size(); i++){
      ReplayFile inFile = inFiles.get(i);
      try {
        //Set up stream
        PacketStream stream = null;

        stream = inFile.getPacketData(PacketTypeRegistry.get(inputVersion, State.LOGIN)).asPacketStream();

        stream.start();

        List<PacketStream.FilterInfo> filters = new ArrayList<>();
        stream.addFilter(new AppendLauncher.ProgressFilter(meta.getDuration()));
        for (PacketStream.FilterInfo info : filters) {
          stream.addFilter(info.getFilter(), info.getFrom(), info.getTo());
        }

        PacketData data;
        if (out != null) { // Write output
          if(i > 0 ){
            for(int j = 0; j < 2; j++) stream.next();
          }
          long tempTimeOffset = 0;
          while ((data = stream.next()) != null) {
            tempTimeOffset = data.getTime();
            data.setTime(data.getTime() + timeOffset);
            out.write(data);
          }
          timeOffset += tempTimeOffset;
          for (PacketData d : stream.end()) {
             out.write(d);
          }

        } else { // Drop output
          System.out.println("out is null");
        }

        in.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }


    out.close();
  }

  private static class ProgressFilter implements StreamFilter {

    private final long total;
    private int lastUpdate;

    public ProgressFilter(long total) {
      this.total = total;
    }

    @Override
    public String getName() {
      return "progress";
    }

    @Override
    public void init(Studio studio, JsonObject config) {

    }

    @Override
    public void onStart(PacketStream stream) {
      lastUpdate = -1;
    }

    @Override
    public boolean onPacket(PacketStream stream, PacketData data) {
      int pct = (int) (data.getTime() * 100 / total);
      if (pct > lastUpdate) {
        lastUpdate = pct;
        System.out.print("Processing... " + pct + "%\r");
      }
      return true;
    }

    @Override
    public void onEnd(PacketStream stream, long timestamp) {
      System.out.println();
    }
  }
}


