/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

/**
 * Keep a list of popular frequencies that most people would use with a scanner,
 * and on selection of a range, implement a scan of those frequencies.
 * 
 * @author Coz
 */
public class ChannelChart {
    File defaultFreqFile;
    File freqDirectory;
    String[][] freqData = null;
    JTable freqTable;
    JScrollPane tableScrollPane;
    final String FILE_SEP = System.getProperty("file.separator");
    JRX_TX appFrame;
    DefaultTableModel model;


    
    public ChannelChart(JRX_TX frame) {
        appFrame = frame;
        freqTable = new javax.swing.JTable();
        model = new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        );
        freqTable.setModel(model);
        tableScrollPane = new javax.swing.JScrollPane();
        tableScrollPane.setViewportView(freqTable);
        appFrame.memoryPanel.add(tableScrollPane, "tableCard");
        
    }

    public void init() {
        freqDirectory = new File(appFrame.config.userPath + FILE_SEP + "frequencies");
        if (!freqDirectory.exists()) {
            freqDirectory.mkdirs();
        }
        defaultFreqFile = new File(freqDirectory + FILE_SEP + "default.tsv");
        writeFreqTable();
        populateFreqTable();
        
    }
    
    private void populateFreqTable() {
        int row = 0;
        String[] header = null;
        ArrayList<ArrayList<String>> freqList = new ArrayList<>();
        ArrayList<String> fields;
        try {
            File[] list = freqDirectory.listFiles();
            Arrays.sort(list);
            for (File fn : list) {
                String name = fn.getName();
                if (name.matches("(?i).*\\.tsv$")) {
                    name = name.replaceFirst("(?i)\\.tsv$", "");
                    List<String> records = Files.readAllLines(Paths.get(fn.toString()), Charset.forName("UTF-8"));
                    int frow = 0;
                    for (String record : records) {
                        fields = new ArrayList<>(Arrays.asList(record.split("\t")));
                        if (fields.size() > 2) {
                            if (frow == 0 && row == 0) {
                                fields.add(0, "File");
                                header = (String[]) fields.toArray(new String[]{});
                            } else {
                                if (frow != 0) {
                                    fields.add(0, name);
                                    freqList.add(fields);
                                }
                            }
                            frow++;
                            row++;
                        }
                    }
                }
            }
            int i = 0;
            freqData = new String[row][];
            for (ArrayList<String> ao : freqList) {
                freqData[i] = (String[]) ao.toArray(new String[]{});
                i++;
            }
            DefaultTableModel dtm = new DefaultTableModel(freqData, header) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            freqTable.setModel(dtm);
            MyTableCellRenderer mr = new MyTableCellRenderer();
            freqTable.setDefaultRenderer(Object.class, mr);
            freqTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            ListSelectionModel rowSM = freqTable.getSelectionModel();
            rowSM.addListSelectionListener(appFrame);                   
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }
    
    public String getValue(int row, int col) {
        String value = freqData[row][col];
        return (value);
    }
    
    public int[] getSelectRows() {
        int[] selection = freqTable.getSelectedRows();
        for (int i = 0; i < selection.length; i++) {
            selection[i] = freqTable.convertRowIndexToModel(selection[i]);
        }
        // selection is now in terms of the underlying TableModel
        return selection;
    }
    
    /**
     * Write the default channel chart to file: "frequencies/default.tsv" when
     * that file does not exist.
     */
    private void writeFreqTable() {
        try {
            if (!defaultFreqFile.exists()) {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("frequencies/default.tsv")) {
                    InputStreamReader isr = new InputStreamReader(is);
                    BufferedReader br = new BufferedReader(isr);
                    String line;
                    try (FileWriter fw = new FileWriter(defaultFreqFile)) {
                        while ((line = br.readLine()) != null) {
                            fw.write(line + "\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }
    
    
}
