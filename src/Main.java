import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final char[] charset = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_".toCharArray();
    private static final long[] weight = {
            0x00000001L, 0x00000021L, 0x00000441L, 0x00008C61L,
            0x00121881L, 0x025528A1L, 0x4CFA3CC1L, 0xEC41D4E1L,
            0x747C7101L, 0x040A9121L, 0x855CB541L, 0x30F35D61L,
            0x4F5F0981L, 0x3B4039A1L, 0xA3476DC1L, 0x0C3525E1L,
    };

    private static final Map<Integer, String> textMap = new HashMap<>();

    private static volatile boolean shouldStop = false;
    private static volatile boolean isPaused = false;
    private static volatile boolean isRunning = false;
    private static volatile long currentProgress = 0;
    private static volatile AtomicLong lastAttempt = new AtomicLong();
    private static volatile AtomicLong attempts = new AtomicLong();



    private static String lastPattern = null;
    private static long lastHash = 0;

    private static ExecutorService pool;
    private static List<Future<?>> runningTasks = new ArrayList<>();
    private static Thread loggerThread;

    private static final JTextField idField = new JTextField(20);
    private static final JTextField hashField = new JTextField(6);

    private static final JTextArea localization = new JTextArea();
    private static final JTextArea resultArea = new JTextArea();

    private static final JButton encodeBtn = new JButton("计算");
    private static final JButton stopBtn = new JButton("停止");

    private static final JLabel progressLabel = new JLabel("当前未执行任何查询");

    public static void main(String[] args) {
        readLocalization();

        JFrame frame = new JFrame("战地1中文ID查询工具");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setResizable(false);
        frame.setLayout(new BorderLayout());

        JTextArea descriptionArea = new JTextArea(
                """
                使用说明：在EAID处填写你所想要计算的EAID，将你的ID可变位改为@，在目标Hash值填写你所希望的八位十六进制的Hash值
                方式一     在EAID处填写如Satori_@@@@@@@, 在Hash值处填写 7D543A64（对应文本：皇帝）后点击确认
                                生成器将会在接下来尝试替换@为任意合法字符进行计算匹配，并尝试输出可行的ID。
                方式二     在EAID处填写如@@_Koishi_@@, 在Hash值处填写 0 后点击确认后，将开始进行任意Hash的匹配
                                生成器将会在接下来尝试替换@为任意合法字符进行暴力计算匹配，并尝试输出任意可匹配为中文名的ID。
                注：  使用方法一时，推荐最多7个通配符@，且尽量将后几位设置为通配符@，暴力计算匹配可能会很耗时
                          如果觉得慢的话睡一觉起来后台挂一个就好了，应该能匹配的上
                          使用方法二时，不推荐设置太多通配符@，设置太多会导致匹配上的中文id能有上千上万
                Credit to BV1yEmYYdEH3 && ChatGPT, and Satori my beloved~
                """
        );
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setMargin(new Insets(10, 10, 10, 10));

        frame.add(descriptionArea, BorderLayout.NORTH);

        // EAID & HASH
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        encodeBtn.addActionListener(e -> {
            if (!isRunning) {
                try {
                    long hash = Long.parseUnsignedLong(hashField.getText(), 16);
                    decodeParallel(idField.getText(), hash);
                    encodeBtn.setText("暂停");
                } catch (Exception ex) {
                    resultArea.append("\nHash 输入有误: " + ex.getMessage());
                }
            } else if (!isPaused) {
                pauseCurrentTask();
                encodeBtn.setText("继续");
            } else {
                resumeCurrentTask();
                encodeBtn.setText("暂停");
            }
        });

        stopBtn.addActionListener(e -> {
            stopCurrentTask();
            encodeBtn.setText("计算");
        });


        inputPanel.add(new JLabel("EAID:"));
        inputPanel.add(idField);
        inputPanel.add(new JLabel("Hash:"));
        inputPanel.add(hashField);
        inputPanel.add(encodeBtn);
        inputPanel.add(stopBtn);

        inputPanel.add(progressLabel);

        frame.add(inputPanel, BorderLayout.CENTER);

        // ID查询 & 输出
        JPanel outputPanel = new JPanel();
        outputPanel.setLayout(new GridLayout(1, 2, 10, 10));

        localization.setEditable(false);
        JScrollPane resultScroll1 = new JScrollPane(localization,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        JScrollPane resultScroll2 = new JScrollPane(resultArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        outputPanel.add(resultScroll1);
        outputPanel.add(resultScroll2);
        outputPanel.setPreferredSize(new Dimension(780, 350));

        frame.add(outputPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    public static void readLocalization(){
        try (BufferedReader reader = new BufferedReader(new FileReader("src/name.txt"))) {
            String line;
            Pattern pattern = Pattern.compile("^(\\p{XDigit}+),\"(.*)\"$");

            StringBuilder sb = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    int key = Integer.parseUnsignedInt(matcher.group(1), 16);
                    String value = matcher.group(2);
                    textMap.put(key, value);
                    sb.append(String.format("%08X", key)).append(":").append(value).append('\n');
                }
            }
            localization.setText(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void decodeParallel(String pattern, long hashValue) {
        if (!isPaused) {
            stopCurrentTask();
            currentProgress = 0;
            resultArea.setText("开始对" + pattern + "进行处理");
        }

        shouldStop = false;
        isPaused = false;
        isRunning = true;
        lastPattern = pattern;
        lastHash = hashValue;

        if (pattern.length() > 16) {
            resultArea.setText("EAID 过长喵");
            return;
        }

        int size = pattern.length();
        String newPattern = pattern.replace('@', '!');
        long baseHash = encode(newPattern);

        List<Integer> wildcardIndices = new ArrayList<>();
        for (int i = 0; i < newPattern.length(); i++) {
            if (newPattern.charAt(i) == '!') {
                wildcardIndices.add(i);
            }
        }

        int wildcardCount = wildcardIndices.size();
        int charsetLen = charset.length;
        long mod = 0x100000000L;
        long total = (long) Math.pow(charsetLen, wildcardCount);

        int threadCount = Runtime.getRuntime().availableProcessors();
        pool = Executors.newFixedThreadPool(Math.max(1, threadCount / 10));
        attempts.set(0);
        long startTime = System.currentTimeMillis();

        loggerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                long currentAttempts = attempts.get() + lastAttempt.get();
                double percent = (currentAttempts * 100.0) / total;
                double rate = currentAttempts / ((System.currentTimeMillis() - startTime) / 1000.0);
                double eta = (total - currentAttempts) / rate;
                progressLabel.setText(String.format("%,d / %,d (%.4f%%), ETA %.1f 秒%n", currentAttempts, total, percent, eta));
            }
        });
        loggerThread.setDaemon(true);
        loggerThread.start();

        long chunk = total / threadCount;
        for (int t = 0; t < threadCount; t++) {
            long start = t * chunk;
            long end = (t == threadCount - 1) ? total : start + chunk;

            Future<?> task = pool.submit(() -> {
                char[] attempt = newPattern.toCharArray();
                for (long i = start; i < end && !shouldStop; i++) {
                    if (i < currentProgress) continue;
                    long idx = i;
                    long extraHash = 0;

                    for (int j = 0; j < wildcardCount; j++) {
                        int charIndex = (int) (idx % charsetLen);
                        idx /= charsetLen;

                        int pos = wildcardIndices.get(j);
                        char ch = charset[charIndex];
                        attempt[pos] = ch;

                        int weightIndex = size - pos - 1;
                        extraHash = (extraHash + (weight[weightIndex] * (ch - '!')) % mod) % mod;
                    }

                    long totalHash = (baseHash + extraHash) % mod;
                    if (hashValue == 0) {
                        if (textMap.containsKey((int) totalHash)) {
                            resultArea.append("\n查询 " + new String(attempt) + " 的中文ID：" + textMap.get((int) totalHash));
                            resultArea.setCaretPosition(resultArea.getDocument().getLength());
                        }
                    } else if (totalHash == hashValue) {
                        resultArea.append("\n查询 " + new String(attempt) + " 的中文ID：" + textMap.get((int) hashValue));
                        resultArea.setCaretPosition(resultArea.getDocument().getLength());
                    }

                    currentProgress = i;
                    attempts.incrementAndGet();
                }
            });
            runningTasks.add(task);
        }

        new Thread(() -> {
            for (Future<?> f : runningTasks) {
                try {
                    f.get();
                } catch (Exception ignored) {}
            }
            if (!isPaused) {
                isRunning = false;
                encodeBtn.setText("计算");
                resultArea.append("\n任务已完成喵");
            }
        }).start();
    }

    public static void stopCurrentTask() {
        shouldStop = true;
        isRunning = false;
        isPaused = false;
        currentProgress = 0;
        lastAttempt.set(0);
        if (pool != null) pool.shutdownNow();
        if (loggerThread != null && loggerThread.isAlive()) loggerThread.interrupt();
        runningTasks.clear();
    }

    public static void pauseCurrentTask() {
        isPaused = true;
        shouldStop = true;
        lastAttempt.addAndGet(attempts.get());
        if (pool != null) pool.shutdownNow();
        if (loggerThread != null && loggerThread.isAlive()) loggerThread.interrupt();
    }

    public static void resumeCurrentTask() {
        decodeParallel(lastPattern, lastHash);
    }

    public static long encode(String input) {
        long result = 0;
        for (int i = 0; i < input.length(); i++) {
            int value = input.charAt(i) - 32;
            result = ((result * 33) & 0xFFFFFFFFL) + value;
        }
        return (result - 1) & 0xFFFFFFFFL;
    }
}