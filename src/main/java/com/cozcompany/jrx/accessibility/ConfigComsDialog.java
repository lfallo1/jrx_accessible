/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import javax.swing.JDialog;

/**
 *
 * @author lutusp
 */
public class ConfigComsDialog extends javax.swing.JDialog 
        implements Configurable {

    JRX_TX parent;
    boolean accept;
    String speech = "If you aren't having any communication speed difficulties,"+
            "then there's no reason to change these settings.\n\n"+
            "But to optimize communications between JRX, the Hamlib library "+
            "and your radio, you may want to adjust the settings below.\n\n" +
            "To use the Hamlib defaults, deselect \"Make and use custom settings\" "+
            "below and exit this dialog.\n\nTo customize the settings,"+
            "select \"Make and use custom settings\", enter your settings, "+
            "then exit and re-enter JRX to let the settings take effect.\n\n"+
            "Your settings are preserved between program runs.";

            
    public ConfigComsDialog(JRX_TX p, boolean modal,int wd,int pwd,int re,int ti,boolean useCustom) {
        super(p, modal);
        parent = p;
        initComponents();
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(600,400);
        setLocationRelativeTo(parent);
        messageTextArea.setText(speech);
        messageTextArea.setFont(parent.baseFont);
        messageTextArea.setCaretPosition(0);
        writeDelayTextField.setText(""+wd);
        postWriteDelayTextField.setText(""+pwd);
        retriesTextField.setText(""+re);
        timeoutTextField.setText(""+ti);
        customCheckBox.setSelected(useCustom);
        updateControls();
    }
    
    private void updateControls() {
        accept = customCheckBox.isSelected();
        writeDelayTextField.setEnabled(accept);
        postWriteDelayTextField.setEnabled(accept);
        retriesTextField.setEnabled(accept);
        timeoutTextField.setEnabled(accept);
    }
    
    private void setupExit(boolean outcome) {
        accept &= outcome;
        setVisible(false);
        dispose();
    }
    
    @Override
    public void fromString(String s) {
        
    }
            
    
    @Override
    public String toString() {
        return "";
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jScrollPane1 = new javax.swing.JScrollPane();
        messageTextArea = new javax.swing.JTextArea();
        dialogControlPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        writeDelayTextField = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        postWriteDelayTextField = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        timeoutTextField = new javax.swing.JTextField();
        customCheckBox = new javax.swing.JCheckBox();
        dlgOkButton = new javax.swing.JButton();
        dlgCancelButton = new javax.swing.JButton();
        jLabel4 = new javax.swing.JLabel();
        retriesTextField = new javax.swing.JTextField();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        messageTextArea.setColumns(20);
        messageTextArea.setLineWrap(true);
        messageTextArea.setRows(5);
        messageTextArea.setWrapStyleWord(true);
        jScrollPane1.setViewportView(messageTextArea);

        getContentPane().add(jScrollPane1, java.awt.BorderLayout.CENTER);

        dialogControlPanel.setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Write delay ms:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        dialogControlPanel.add(jLabel1, gridBagConstraints);

        writeDelayTextField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        writeDelayTextField.setText("000");
        writeDelayTextField.setMinimumSize(new java.awt.Dimension(70, 19));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        dialogControlPanel.add(writeDelayTextField, gridBagConstraints);

        jLabel2.setText("Post write delay ms:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        dialogControlPanel.add(jLabel2, gridBagConstraints);

        postWriteDelayTextField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        postWriteDelayTextField.setText("000");
        postWriteDelayTextField.setMinimumSize(new java.awt.Dimension(70, 19));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        dialogControlPanel.add(postWriteDelayTextField, gridBagConstraints);

        jLabel3.setText("Timeout ms:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        dialogControlPanel.add(jLabel3, gridBagConstraints);

        timeoutTextField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        timeoutTextField.setText("000");
        timeoutTextField.setMinimumSize(new java.awt.Dimension(70, 19));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        dialogControlPanel.add(timeoutTextField, gridBagConstraints);

        customCheckBox.setText("Make and use custom Settings");
        customCheckBox.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                customCheckBoxMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        dialogControlPanel.add(customCheckBox, gridBagConstraints);

        dlgOkButton.setText("OK");
        dlgOkButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                dlgOkButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        dialogControlPanel.add(dlgOkButton, gridBagConstraints);

        dlgCancelButton.setText("Cancel");
        dlgCancelButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                dlgCancelButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        dialogControlPanel.add(dlgCancelButton, gridBagConstraints);

        jLabel4.setText("Retries:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        dialogControlPanel.add(jLabel4, gridBagConstraints);

        retriesTextField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        retriesTextField.setText("000");
        retriesTextField.setMinimumSize(new java.awt.Dimension(70, 19));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        dialogControlPanel.add(retriesTextField, gridBagConstraints);

        getContentPane().add(dialogControlPanel, java.awt.BorderLayout.SOUTH);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void customCheckBoxMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_customCheckBoxMouseClicked
        // TODO add your handling code here:
        updateControls();
    }//GEN-LAST:event_customCheckBoxMouseClicked

    private void dlgOkButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dlgOkButtonMouseClicked
        // TODO add your handling code here:
        setupExit(true);
    }//GEN-LAST:event_dlgOkButtonMouseClicked

    private void dlgCancelButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dlgCancelButtonMouseClicked
        // TODO add your handling code here:
        setupExit(false);
    }//GEN-LAST:event_dlgCancelButtonMouseClicked

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(ConfigComsDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        //</editor-fold>

        /* Create and display the dialog */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                //ConfigComsDialog dialog = new ConfigComsDialog(new javax.swing.JFrame(), true);
                //dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                  //  @Override
                   // public void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                //});
                //dialog.setVisible(true);
            //}
        });
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox customCheckBox;
    private javax.swing.JPanel dialogControlPanel;
    private javax.swing.JButton dlgCancelButton;
    private javax.swing.JButton dlgOkButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea messageTextArea;
    protected javax.swing.JTextField postWriteDelayTextField;
    protected javax.swing.JTextField retriesTextField;
    protected javax.swing.JTextField timeoutTextField;
    protected javax.swing.JTextField writeDelayTextField;
    // End of variables declaration//GEN-END:variables
}
