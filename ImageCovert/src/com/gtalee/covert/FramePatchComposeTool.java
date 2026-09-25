package com.gtalee.covert;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.SpinnerNumberModel;

public final class FramePatchComposeTool extends JFrame {
    private static final class ProgressInfo { String message; int current,total; ProgressInfo(String m,int c,int t){message=m;current=c;total=t;} }
    private static String lastInputPath = "";
    private static String lastOutputPath = "";
    private final JTextField input = new JTextField(28);
    private final JTextField output = new JTextField(28);
    private final JSpinner threshold = new JSpinner(new SpinnerNumberModel(8, 0, 255, 1));
    private final JSpinner min = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));
    private final JSpinner size = new JSpinner(new SpinnerNumberModel(64, 1, 255, 1));
    private final JButton runButton = new JButton("开始转换并验证");
    private final JLabel status = new JLabel("就绪");
    private final JProgressBar progress = new JProgressBar();

    public FramePatchComposeTool() {
        input.setText(loadPath("framePatch.input", lastInputPath));
        output.setText(loadPath("framePatch.output", lastOutputPath));
        setTitle("帧差补丁转换");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel form = new JPanel(new GridLayout(0, 1));
        form.add(row("输入图片目录", input, true));
        form.add(row("输出目录", output, false));
        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT));
        opts.add(new JLabel("差异阈值"));
        opts.add(threshold);
        opts.add(new JLabel("最小差异像素"));
        opts.add(min);
        opts.add(new JLabel("补丁最大尺寸"));
        opts.add(size);
        form.add(opts);
        add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        progress.setIndeterminate(false);
        progress.setStringPainted(true);
        progress.setString("就绪");
        progress.setPreferredSize(new java.awt.Dimension(500, 22));
        progress.setVisible(false);
        bottom.add(status, BorderLayout.NORTH);
        JPanel action = new JPanel(new BorderLayout(8, 0));
        action.add(progress, BorderLayout.CENTER);
        runButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                startConversion();
            }
        });
        action.add(runButton, BorderLayout.EAST);
        bottom.add(action, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        setSize(820, 240);
        setLocationRelativeTo(null);
    }

    private static String loadPath(String key, String fallback) { String value = java.util.prefs.Preferences.userNodeForPackage(FramePatchComposeTool.class).get(key, fallback); return value == null ? fallback : value; }
    private static void savePath(String key, String value) { java.util.prefs.Preferences.userNodeForPackage(FramePatchComposeTool.class).put(key, value == null ? "" : value); }

    private JPanel row(String label, final JTextField field, final boolean dir) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.add(new JLabel(label));
        p.add(field);
        JButton b = new JButton("选择...");
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser c = new JFileChooser(field.getText());
                c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (c.showOpenDialog(FramePatchComposeTool.this) == JFileChooser.APPROVE_OPTION) {
                    field.setText(c.getSelectedFile().getAbsolutePath());
                    if (dir) {
                        lastInputPath = field.getText(); savePath("framePatch.input", lastInputPath);
                    } else {
                        lastOutputPath = field.getText(); savePath("framePatch.output", lastOutputPath);
                    }
                }
            }
        });
        p.add(b);
        return p;
    }

    private void startConversion() {
        final File in = new File(input.getText().trim());
        final File out = new File(output.getText().trim());
        final int thresholdValue = ((Integer) threshold.getValue()).intValue();
        final int minValue = ((Integer) min.getValue()).intValue();
        final int sizeValue = ((Integer) size.getValue()).intValue();
        runButton.setEnabled(false);
        status.setText("正在转换，请稍候...");
        progress.setString("正在转换，请稍候...");
        progress.setIndeterminate(true);
        progress.setVisible(true);

        SwingWorker<FramePatchComposeBuilder.Result, ProgressInfo> worker =
            new SwingWorker<FramePatchComposeBuilder.Result, ProgressInfo>() {
                protected FramePatchComposeBuilder.Result doInBackground() throws Exception {
                    return FramePatchComposeBuilder.build(
                        in, out, thresholdValue, 0, minValue, sizeValue,
                        256, 128, false,
                        new FramePatchComposeBuilder.ProgressListener() {
                            public void onProgress(String message, int current, int total) {
                                publish(new ProgressInfo(message, current, total));
                            }
                        }
                    );
                }

                protected void process(java.util.List<ProgressInfo> chunks) {
                    if (chunks == null || chunks.size() == 0) {
                        return;
                    }
                    ProgressInfo info = chunks.get(chunks.size() - 1);
                    status.setText(info.message);
                    progress.setString(info.message);
                    if (info.total > 0) {
                        progress.setIndeterminate(false);
                        progress.setMaximum(info.total);
                        progress.setValue(Math.min(info.current, info.total));
                    }
                }

                protected void done() {
                    progress.setIndeterminate(false);
                    progress.setVisible(false);
                    runButton.setEnabled(true);
                    try {
                        FramePatchComposeBuilder.Result r = get();
                        status.setText("转换完成");
                        progress.setString("转换完成");
                        JOptionPane.showMessageDialog(
                            FramePatchComposeTool.this,
                            "完成：" + r.frameCount + "帧，生成" + r.patchCount
                                + "个图块\\n配置文件："
                                + new File(out, "frame_patches.compose.properties")
                        );
                    } catch (Exception e) {
                        status.setText("转换失败");
                        progress.setString("转换失败");
                        Throwable cause = e.getCause();
                        if (cause == null) {
                            cause = e;
                        }
                        String message = cause.getMessage();
                        if (message == null || message.length() == 0) {
                            message = cause.toString();
                        }
                        JOptionPane.showMessageDialog(
                            FramePatchComposeTool.this,
                            message,
                            "转换失败",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
            };
        worker.execute();
    }
}