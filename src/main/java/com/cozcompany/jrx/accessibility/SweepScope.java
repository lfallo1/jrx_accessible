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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JPanel;

/**
 *
 * @author lutusp
 */
final public class SweepScope extends JPanel implements MouseMotionListener {

    JRX_TX parent;
    final ArrayList<Pair<Double>> scopeData;
    ArrayList<MemoryButton> scanLimits;
    double xgmin, xgmax;
    double ygmin, ygmax;
    double xmin = 0, xmax = 400;
    double ymin, ymax;
    double scanStep;
    double old_y = 0;
    int scanSpeedMs;
    long startFreq;
    long currentFreq = -1;
    boolean running = false;
    ArrayList<Pair> userDataList;
    Color dataColor, gridColor, bgColor, zeroColor, lineColor;
    Timer sweepTimer, paintTimer;
    int repaints = 0;
    String toolTip = "";
    String help = "\n(To set a sweep range, click the upper or lower button\nof a pair of memory buttons)";

    public SweepScope(JRX_TX p) {
        parent = p;
        scopeData = new ArrayList<>();
        bgColor = Color.black;
        dataColor = Color.red;
        lineColor = new Color(128, 192, 255);
        gridColor = new Color(96, 96, 96);
        zeroColor = new Color(192, 0, 192);
        setBackground(Color.black);
        setToolTipText(toolTip);
        setup();
    }

    protected void setup() {
        addMouseMotionListener(this);
        setDefaults();
    }

    private void setDefaults() {
        ymin = parent.squelchLow;
        ymax = parent.squelchHigh;
        repaint();
    }

    public boolean isRunning() {
        return running;
    }

    private void setParams() {
        String label = (isRunning()) ? "Stop" : "Start";
        parent.scopeStartStopButton.setText(label);
    }

    public void startSweep() {
        setDefaults();
        parent.scanStateMachine.stopScan(false);
        if (!isRunning()) {
            setupRunSweep();
        } else {
            stopSweep(false);
        }
        setParams();
    }

    private void setupRunSweep() {
        scanLimits = parent.memoryCollection.getScanButtons(2);
        if (scanLimits != null && scanLimits.size() >= 2) {

            MemoryButton low = scanLimits.get(0);
            MemoryButton high = scanLimits.get(1);
            if (low.frequency > high.frequency) {
                MemoryButton temp = low;
                low = high;
                high = temp;
            }
            xmin = low.frequency;
            startFreq = low.frequency;
            currentFreq = startFreq;
            xmax = high.frequency;
            setScanParams();
            runSweep();
        } else {
            parent.tellUser("Please click the lower of two memory buttons.");
        }
    }

    private void runSweep() {
        running = true;
        old_y = parent.freqStrength(currentFreq);
        sweepTimer = new java.util.Timer();
        sweepTimer.scheduleAtFixedRate(new SweepEvents(), 100, scanSpeedMs);
        paintTimer = new java.util.Timer();
        paintTimer.scheduleAtFixedRate(new PaintEvents(false), 100, 500);
        setParams();
    }

    class SweepEvents extends TimerTask {

        @Override
        public void run() {
            if (running) {
                double y = parent.freqStrength(currentFreq);
                if (y == -1e30) {
                    y = old_y;
                }
                old_y = y;
                if (currentFreq >= 0) {
                    synchronized (scopeData) {
                        scopeData.add(new Pair<>((double) currentFreq, y));
                    }
                    currentFreq += scanStep;
                    if (currentFreq > xmax) {
                        stopSweep(false);
                    }
                }
            } else if (sweepTimer != null) {
                sweepTimer.cancel();
            }
        }
    }

    class PaintEvents extends TimerTask {

        boolean force;

        public PaintEvents(boolean f) {
            force = f;
        }

        @Override
        public void run() {
            if (running || force) {
                repaint();
            } else if (paintTimer != null) {
                paintTimer.cancel();
            }
        }
    }

    protected void stopSweep(boolean resetFreq) {
        running = false;
        currentFreq = -1;
        if (resetFreq) {
            parent.freqStrength(startFreq);
        }
        // draw once more
        new Timer().schedule(new PaintEvents(true), 100);
        setParams();
    }

    private void setScanParams() {
        scopeData.clear();
        if (parent.validSetup()) {
            String ss = (String) parent.sv_scopeStepComboBox.getSelectedItem();
            scanStep = parent.scanDude.scanSteps.get(ss);
            String ts = (String) parent.sv_scopeSpeedComboBox.getSelectedItem();
            scanSpeedMs = parent.scanDude.timeSteps.get(ts);
        }
    }

    private boolean autoscale2() {
        if (scopeData != null) {
            ymin = 1e30;
            ymax = -1e30;
            for (Pair<Double> p : scopeData) {
                ymin = Math.min(p.y, ymin);
                ymax = Math.max(p.y, ymax);
            }
            double range = ymax - ymin;
            ymin -= range * .1;
            ymax += range * .1;
            return true;
        } else {
            return false;
        }
    }

    protected void autoscale() {
        if (!autoscale2()) {
            Beep.beep();
        } else {
            repaint();
        }
    }

    void drawScale(Graphics2D cg) {
        if (xmax <= xmin || ymax <= ymin) {
            return;
        }

        Stroke dotted = new BasicStroke(
                2.0f,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_BEVEL,
                1.0f,
                new float[]{2.0f, 3.0f},
                0.0f);

        Stroke deflt = cg.getStroke();

        cg.setStroke(dotted);

        double scaleFact = 5.0;

        double ystep = Math.abs((ymax - ymin)) / 5.0;

        double s = Math.log(ystep) / Math.log(scaleFact);

        s = Math.floor(s);

        ystep = Math.pow(scaleFact, s);

        cg.setColor(gridColor);

        for (double y = -ystep; y >= ymin; y -= ystep) {
            if ((y >= ymin && y <= ymax)) {
                drawScaledLine(cg, xmin, y, xmax, y);
            }
        }
        for (double y = ystep; y <= ymax; y += ystep) {
            if ((y >= ymin && y <= ymax)) {
                drawScaledLine(cg, xmin, y, xmax, y);
            }
        }

        double xstep = Math.abs((xmax - xmin)) / 5.0;

        s = Math.log(xstep) / Math.log(scaleFact);

        s = Math.floor(s);

        xstep = Math.pow(scaleFact, s);

        cg.setColor(gridColor);

        for (double x = xstep; x <= xmax; x += xstep) {
            if ((x >= xmin && x <= xmax)) {
                drawScaledLine(cg, x, ymin, x, ymax);
            }
        }
        for (double x = -xstep; x >= xmin; x -= xstep) {
            if ((x >= xmin && x <= xmax)) {
                drawScaledLine(cg, x, ymin, x, ymax);
            }
        }

        // horizontal zero line

        cg.setStroke(deflt);

        cg.setColor(zeroColor);

        drawScaledLine(cg, xmin, 0, xmax, 0);
    }

    void drawDataPoints(Graphics2D cg, int dsi) {
        if (scopeData != null) {
            cg.setColor(dataColor);
            synchronized (scopeData) {
                for (Pair<Double> pr : scopeData) {
                    double x = pr.x;
                    double y = pr.y;
                    drawScaledPoint(cg, x, y, dsi);
                }
            }
        }
    }

    void drawCurve(Graphics2D cg) {
        if (scopeData != null) {
            double ox = 0, oy = 0;
            boolean first = true;
            cg.setColor(lineColor);
            synchronized (scopeData) {
                for (Pair<Double> p : scopeData) {
                    if (!first) {
                        drawScaledLine(cg, ox, oy, p.x, p.y);
                    }
                    ox = p.x;
                    oy = p.y;
                    first = false;
                }
            }
        }
    }

    boolean compScreen() {
        boolean result = false;
        int w = getSize().width;
        int h = getSize().height;
        if (w > 0 && h > 0) {

            xgmin = 1;
            ygmin = 1;
            xgmax = w - 2;
            ygmax = h - 2;
            result = true;
        }
        return result;
    }

    void drawScaledLine(Graphics g, double ox, double oy, double x, double y) {
        int opx, opy, px, py;
        opx = parent.intrp(xmin, xmax, xgmin, xgmax, ox);
        px = parent.intrp(xmin, xmax, xgmin, xgmax, x);
        opy = parent.intrp(ymax, ymin, ygmin, ygmax, oy);
        py = parent.intrp(ymax, ymin, ygmin, ygmax, y);
        g.drawLine(opx, opy, px, py);
        if (y < ymin || y > ymax) {
            autoscale2();
        }
    }

    void drawScaledPoint(Graphics g, double x, double y, int dsi) {
        int px = parent.intrp(xmin, xmax, xgmin, xgmax, x);
        int py = parent.intrp(ymax, ymin, ygmin, ygmax, y);
        g.fillOval(px, py, dsi, dsi);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        repaints += 1;
        Graphics2D cg = (Graphics2D) g;
        RenderingHints rh = new RenderingHints(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        cg.addRenderingHints(rh);

        if (compScreen()) {
            drawScale(cg);
            if (scopeData != null) {
                drawCurve(cg);
                if (parent.sv_scopeDotsCheckBox.isSelected()) {
                    drawDataPoints(cg, 2);
                }
            }
        }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        return toolTip;
    }

    private String convertx(double x) {
        String[] scales = new String[]{"Hz", "KHz", "MHz", "GHz"};
        int i = 0;
        double sc = 1;
        while (x > sc * 1000 && i < scales.length - 1) {
            sc *= 1000;
            i++;
        }
        return String.format("%.4f %s", x / sc, scales[i]);
    }

    @Override
    public void mouseMoved(MouseEvent me) {
        int w = getWidth();
        int h = getHeight();

        double mx = me.getPoint().getX();
        double my = me.getPoint().getY();
        double qx = parent.ntrp(0, w, xmin, xmax, mx);
        double qy = parent.ntrp(0, h, ymin, ymax, h - my);
        String sx = convertx(qx);
        toolTip = String.format("x = %s, y = %.4f", sx, qy);
        //parent.p(tt);
    }

    @Override
    public void mouseDragged(MouseEvent me) {
        mouseMoved(me);
    }

    protected void saveData() {
        if (scopeData == null) {
            Beep.beep();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Frequency,Amplitude\n");
            for (Pair p : scopeData) {
                sb.append(String.format("%.0f,%.2f\n", p.x, p.y));
            }
            parent.writeToClipboard(sb.toString());
        }
    }
}
