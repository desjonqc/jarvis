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
        if (Jarvis.getLogger() == null){
            super.println(x);
            return;
        }
        if (x.contains("\n")){
            for (String s : x.split("\n"))
                Jarvis.getLogger().info(s);
        } else
            Jarvis.getLogger().info(x);
    }

    public void w(String s){
        super.println(s);
    }
}
