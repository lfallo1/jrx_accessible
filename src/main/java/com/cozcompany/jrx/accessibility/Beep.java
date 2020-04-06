// ***************************************************************************
// *   Copyright (C) 2012 by Paul Lutus                                      *
// *   lutusp@arachnoid.com                                                  *
// *                                                                         *
// *   This program is free software; you can redistribute it and/or modify  *
// *   it under the terms of the GNU General Public License as published by  *
// *   the Free Software Foundation; either version 2 of the License, or     *
// *   (at your option) any later version.                                   *
// *                                                                         *
// *   This program is distributed in the hope that it will be useful,       *
// *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
// *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
// *   GNU General Public License for more details.                          *
// *                                                                         *
// *   You should have received a copy of the GNU General Public License     *
// *   along with this program; if not, write to the                         *
// *   Free Software Foundation, Inc.,                                       *
// *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
// ***************************************************************************

package com.cozcompany.jrx.accessibility;

import javax.sound.sampled.*;

final public class Beep extends Thread {

    float sampleRate = 32000;
    int freqHz;
    int durationMsec;
    double level;

    // 0 <= level <= 1.0
    public Beep(int freqHz, int durationMsec, double level) {
        this.freqHz = freqHz;
        this.durationMsec = durationMsec;
        this.level = level * 32767;
        start();
    }

    private double envelope(double a, double b, double t, double tc) {
        return ((b - t) * (-a + t)) / ((b - t + tc) * (-a + t + tc));
    }

    public void run() {
        try {
            int bsize = (int) (2 * sampleRate * durationMsec / 1000);
            byte[] buf = new byte[bsize];
            double step = 2.0 * Math.PI * freqHz / sampleRate;
            double angle = 0;
            int i = 0;
            while (i  < bsize) {
                int n = (int) (Math.sin(angle) * level * envelope(0,bsize,i,1000));
                buf[i++] = (byte) (n % 256);
                buf[i++] = (byte) (n / 256);
                angle += step;
            }

            AudioFormat af = new AudioFormat(sampleRate, 16, 1, true, false);
            SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
            sdl.open(af);
            sdl.start();
            sdl.write(buf, 0, buf.length);
            sdl.drain();
            sdl.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void beep(double level) {
        new Beep(1000, 100, level);
    }

    public static void beep() {
        new Beep(1000, 100, .5);
    }

    public static void main(String[] args) {
        beep(.5);
    }
}
