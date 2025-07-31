import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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
            0x92D9E201L, 0xEE162221L, 0xB0DA6641L, 0xCC272E61L,
            0x510CFA81L, 0x72AC4AA1L,
    };
    private static final Map<Character, char[]> fuzzyMap = Map.of(
            'i', new char[]{'i', 'l', 'I'},
            'l', new char[]{'i', 'l', 'I'},
            'I', new char[]{'i', 'l', 'I'},
            '0', new char[]{'0', 'O'},
            'O', new char[]{'0', 'O'}
    );

    private static final Map<Integer, String> textMap = new HashMap<>();
    private static final AtomicLong attempts = new AtomicLong();

    private static final AtomicBoolean shouldStop = new AtomicBoolean(false);
    private static final ExecutorService pool = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 5));

    private static final JTextField idField = new JTextField(20);
    private static final JTextField hashField = new JTextField(6);
    private static final JTextArea localization = new JTextArea();
    private static final JTextArea resultArea = new JTextArea();
    private static final JButton encodeBtn = new JButton("����");
    private static final JButton stopBtn = new JButton("��ֹ");
    private static final JLabel progressLabel = new JLabel("��ǰδִ���κβ�ѯ");

    private static volatile boolean isRunning = false;

    public static void main(String[] args) {
        readLocalization();
        SwingUtilities.invokeLater(Main::createGUI);
    }

    private static void createGUI() {
        JFrame frame = new JFrame("ս��1����ID��ѯ���� v0.0.2");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setResizable(false);
        frame.setLayout(new BorderLayout());

        JTextArea descriptionArea = new JTextArea("""
                ʹ��˵������EAID����д������Ҫ�����EAID�������ID�ɱ�λ��Ϊ@����Ŀ��Hashֵ��д����ϣ���İ�λʮ�����Ƶ�Hashֵ
                ��ʽһ     ��EAID����д��Satori_@@@@@@@, ��Hashֵ����д 7D543A64����Ӧ�ı����ʵۣ�����ȷ��
                                �����������ڽ����������滻@Ϊ����Ϸ��ַ����м���ƥ�䣬������������е�ID��
                ��ʽ��     ��EAID����д��@@_Koishi_@@, ��Hashֵ����д 0 ����ȷ�Ϻ󣬽���ʼ��������Hash��ƥ��
                                �����������ڽ����������滻@Ϊ����Ϸ��ַ����б�������ƥ�䣬��������������ƥ��Ϊ��������ID��
                ע��  ʹ�÷���һʱ���Ƽ����7��ͨ���@���Ҿ�������λ����Ϊͨ���@����������ƥ����ܻ�ܺ�ʱ
                          ����������Ļ�˯һ��������̨��һ���ͺ��ˣ�Ӧ����ƥ�����
                          ʹ�÷�����ʱ�����Ƽ�����̫��ͨ���@������̫��ᵼ��ƥ���ϵ�����id������ǧ����
                Repo: https://github.com/LaoHuaJiOfficial/EAID_Generator, Credit to BV1yEmYYdEH3, ChatGPT and beloved Satori
                """);
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setMargin(new Insets(10, 10, 10, 10));
        frame.add(descriptionArea, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        encodeBtn.addActionListener(e -> startComputation());
        stopBtn.addActionListener(e -> {
            shouldStop.set(true);
            resultArea.setText("");
        });
        inputPanel.add(new JLabel("EAID:"));
        inputPanel.add(idField);
        inputPanel.add(new JLabel("Hash:"));
        inputPanel.add(hashField);
        inputPanel.add(encodeBtn);
        inputPanel.add(stopBtn);
        inputPanel.add(progressLabel);
        frame.add(inputPanel, BorderLayout.CENTER);

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
        outputPanel.setPreferredSize(new Dimension(780, 320));

        frame.add(outputPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }


    private static void startComputation() {
        if (isRunning) return;
        isRunning = true;
        encodeBtn.setText("������");
        shouldStop.set(false);
        attempts.set(0);

        long hash;
        try {
            hash = Long.parseUnsignedLong(hashField.getText(), 16);
        } catch (Exception ex) {
            resultArea.append("\nHash ��������: " + ex.getMessage());
            isRunning = false;
            encodeBtn.setText("����");
            return;
        }

        String text = idField.getText();
        System.out.println(text);
        decodeParallel(text, hash);
    }

    public static void decodeParallel(String pattern, long hashValue) {
        if (pattern.length() > 22) {
            resultArea.setText("EAID ������\n");
            return;
        }

        int size = pattern.length();
        String newPattern = pattern.replace('@', '!');
        long baseHash = encode(newPattern);
        List<Integer> wildcardIndices = new ArrayList<>();
        for (int i = 0; i < newPattern.length(); i++) {
            if (newPattern.charAt(i) == '!') wildcardIndices.add(i);
        }

        int wildcardCount = wildcardIndices.size();
        int charsetLen = charset.length;
        long mod = 0x100000000L;
        long total = (long) Math.pow(charset.length, wildcardIndices.size());
        long startTime = System.currentTimeMillis();

        Thread logger = new Thread(() -> {
            while (!shouldStop.get() && isRunning) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                long done = attempts.get();
                double percent = done * 100.0 / total;
                double rate = done / ((System.currentTimeMillis() - startTime) / 1000.0);
                double eta = (total - done) / (rate + 0.0001);
                progressLabel.setText(String.format("%,d / %,d (%.2f%%), ETA %.1f ��", done, total, percent, eta));
            }
        });
        logger.setDaemon(true);
        logger.start();

        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 5);
        long chunk = total / threads;
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            long start = t * chunk;
            long end = (t == threads - 1) ? total : start + chunk;
            pool.submit(() -> {
                try {
                    char[] attempt = newPattern.toCharArray();
                    for (long i = start; i < end && !shouldStop.get(); i++) {
                        long idx = i, extra = 0;
                        for (int j = 0; j < wildcardCount; j++) {
                            int charIndex = (int) (idx % charsetLen);
                            idx /= charsetLen;

                            int pos = wildcardIndices.get(j);
                            char ch = charset[charIndex];
                            attempt[pos] = ch;

                            int weightIndex = size - pos - 1;

                            extra = (extra + (weight[weightIndex] * (ch - '!')) % mod) % mod;
                        }
                        long totalHash = (baseHash + extra) % mod;
                        //System.out.println(Arrays.toString(attempt) + " " + extra + " " + Integer.toHexString((int) totalHash));
                        if (hashValue == 0 && textMap.containsKey((int) totalHash)) {
                            appendResult(attempt, textMap.get((int) totalHash));
                        } else if (totalHash == hashValue) {
                            appendResult(attempt, textMap.getOrDefault((int) hashValue, "δ֪"));
                        }
                        attempts.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        new Thread(() -> {
            try {
                latch.await();
            } catch (InterruptedException ignored) {}
            isRunning = false;
            encodeBtn.setText("����");
            resultArea.append("\n�����������\n");
        }).start();
    }

    private static void appendResult(char[] id, String name) {
        resultArea.append("\n��ѯ " + new String(id) + " ������ID��" + name);
        resultArea.setCaretPosition(resultArea.getDocument().getLength());
    }

    public static long encode(String input) {
        long result = 0;
        for (int i = 0; i < input.length(); i++) {
            int value = input.charAt(i) - 32;
            result = ((result * 33) & 0xFFFFFFFFL) + value;
        }
        return (result - 1) & 0xFFFFFFFFL;
    }

    public static void readLocalization() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(Main.class.getResourceAsStream("/name.txt")), StandardCharsets.UTF_8))) {
            String line;
            Pattern pattern = Pattern.compile("^(\\p{XDigit}+),\"(.*)\"$");
            StringBuilder sb = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                Matcher m = pattern.matcher(line);
                if (m.matches()) {
                    int key = Integer.parseUnsignedInt(m.group(1), 16);
                    String value = m.group(2);
                    textMap.put(key, value);
                    sb.append(String.format("%08X", key)).append(":").append(value).append('\n');
                }
            }
            localization.setText(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<String> generateFuzzyVariants(String input) {
        List<Integer> indices = new ArrayList<>();
        List<char[]> opts = new ArrayList<>();
        char[] base = input.toCharArray();
        for (int i = 0; i < base.length; i++) {
            if (fuzzyMap.containsKey(base[i])) {
                indices.add(i);
                opts.add(fuzzyMap.get(base[i]));
            }
        }
        if (indices.isEmpty()) return List.of(input);
        List<String> results = new ArrayList<>();
        int total = opts.stream().mapToInt(a -> a.length).reduce(1, (a, b) -> a * b);
        for (int i = 0; i < total; i++) {
            char[] copy = base.clone();
            int idx = i;
            for (int j = 0; j < indices.size(); j++) {
                copy[indices.get(j)] = opts.get(j)[idx % opts.get(j).length];
                idx /= opts.get(j).length;
            }
            results.add(new String(copy));
        }
        return results;
    }
}
