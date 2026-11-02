package com.cegesoft.jarvis.logging;

import com.cegesoft.jarvis.Jarvis;

import java.io.OutputStream;
import java.io.PrintStream;

public class JPrintStreamImpl extends PrintStream {

    public JPrintStreamImpl(OutputStream stream) {
        super(stream);
    }

    @Override
    public void println(String x) {
        for (String s : String.valueOf(x).split("\n"))
            Jarvis.getLogger().info(s);
    }

    public void w(String s){
        super.println(s);
    }
}
