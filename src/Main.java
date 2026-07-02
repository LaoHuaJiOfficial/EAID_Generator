import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class Main {
    private enum Game {
        BF1("\u6218\u5730\u98CE\u4E91 1", "/BF1Bundle.txt"),
        BF4("\u6218\u5730\u98CE\u4E91 4", "/BF4Bundle.txt"),
        BF1_AND_4("\u6218\u5730\u98CE\u4E91 1&4", "/BF1&4Bundle.txt");
        final String label;
        final String resourcePath;
        Game(String label, String resourcePath) {
            this.label = label;
            this.resourcePath = resourcePath;
        }
    }
    private static final char[] CHARSET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_".toCharArray();
    private static final int CHARSET_LEN = CHARSET.length;
    private static final long MOD = 0x100000000L;
    private static final int MAX_ENCODE_LEN = 22;
    private static final int MAX_SINGLE_ID_LEN = 16;
    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final int MIN_BRACKET_PREFIX_LEN = 3;
    private static final int MAX_BRACKET_PREFIX_LEN = 4;
    private static final int MIN_CHAR_OFFSET;
    private static final int MAX_CHAR_OFFSET;
    private static final long[] WEIGHT = {
            0x00000001L, 0x00000021L, 0x00000441L, 0x00008C61L,
            0x00121881L, 0x025528A1L, 0x4CFA3CC1L, 0xEC41D4E1L,
            0x747C7101L, 0x040A9121L, 0x855CB541L, 0x30F35D61L,
            0x4F5F0981L, 0x3B4039A1L, 0xA3476DC1L, 0x0C3525E1L,
            0x92D9E201L, 0xEE162221L, 0xB0DA6641L, 0xCC272E61L,
            0x510CFA81L, 0x72AC4AA1L,
    };
    private static final Map<Character, char[]> FUZZY_MAP = Map.of(
            'i', new char[]{'i', 'l', 'I'},
            'l', new char[]{'i', 'l', 'I'},
            'I', new char[]{'i', 'l', 'I'},
            '0', new char[]{'0', 'O'},
            'O', new char[]{'0', 'O'}
    );
    private static final Pattern BUNDLE_LINE = Pattern.compile("^(\\p{XDigit}+),\"(.*)\"$");
    static {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (char ch : CHARSET) {
            int offset = ch - '!';
            min = Math.min(min, offset);
            max = Math.max(max, offset);
        }
        MIN_CHAR_OFFSET = min;
        MAX_CHAR_OFFSET = max;
    }
    private static final Map<Game, Map<Integer, String>> TEXT_MAPS = new EnumMap<>(Game.class);
    private static final Map<Game, int[]> SORTED_KEYS = new EnumMap<>(Game.class);
    private static final AtomicBoolean shouldStop = new AtomicBoolean(false);
    private static final JTextField prefixField = new JTextField(4);
    private static final JTextField idField = new JTextField(16);
    private static final JTextField hashField = new JTextField(8);
    private static final JTextField limitField = new JTextField(String.valueOf(DEFAULT_MAX_RESULTS), 4);
    private static final JTextArea localization = new JTextArea();
    private static final JTextArea resultArea = new JTextArea();
    private static final JButton encodeBtn = new JButton("\u8BA1\u7B97");
    private static final JButton stopBtn = new JButton("\u7EC8\u6B62");
    private static final JLabel progressLabel = new JLabel("\u5F53\u524D\u672A\u6267\u884C\u4EFB\u4F55\u67E5\u8BE2");
    private static final JComboBox<Game> gameSelector = new JComboBox<>(Game.values());
    private static volatile Game currentGame = Game.BF1;
    private static volatile boolean isRunning = false;
    public static void main(String[] args) {
        for (Game game : Game.values()) {
            Map<Integer, String> textMap = loadBundle(game);
            TEXT_MAPS.put(game, textMap);
            SORTED_KEYS.put(game, buildSortedKeys(textMap));
        }
        SwingUtilities.invokeLater(Main::createGUI);
    }
    private static void createGUI() {
        JFrame frame = new JFrame("EA ID \u4E2D\u6587\u67E5\u8BE2\u5DE5\u5177 v0.3.0");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 620);
        frame.setResizable(false);
        frame.setLayout(new BorderLayout());
        JTextArea descriptionArea = new JTextArea("""
                \u4F7F\u7528\u8BF4\u660E\uFF1A\u586B\u5199 EAID \u56FA\u5B9A\u524D\u7F00\uFF0C\u751F\u6210\u5668\u5C06\u5728\u5C3E\u90E8\u8FFD\u52A0\u901A\u914D\u7B26\u8FDB\u884C\u5339\u914D\uFF1BHash \u4E3A 0 \u65F6\u5339\u914D\u4EFB\u610F\u4E2D\u6587\u540D
                \u793A\u4F8B     \u5728 EAID \u5904\u586B\u5199 Satori_\uFF0CHash \u5904\u586B\u5199 7D543A64\uFF08\u5BF9\u5E94\u6587\u672C\uFF1A\u7687\u5E1D\uFF09\u540E\u70B9\u51FB\u8BA1\u7B97
                                \u751F\u6210\u5668\u5C06\u5C1D\u8BD5 Satori_\u3001Satori_@\u3001Satori_@@ \u2026 \u7B49\u5F62\u5F0F\uFF0C\u8F93\u51FA\u81F3\u591A N \u4E2A\u53EF\u884C ID
                \u524D\u7F00     \u53EF\u7559\u7A7A\uFF0C\u6216\u586B\u5199 3-4 \u4E2A\u5B57\u7B26\uFF0C\u8BA1\u7B97\u65B9\u5F0F\u4E3A [\u524D\u7F00]+ID
                \u6570\u91CF\u4E0A\u9650  \u63A7\u5236\u6700\u591A\u8F93\u51FA\u7684\u53EF\u884C ID \u6570\u91CF\uFF0C\u9ED8\u8BA4\u4E3A 10
                \u6218\u57301&4  \u9009\u62E9\u300C\u6218\u5730\u98CE\u4E91 1&4\u300D\u65F6\uFF0C\u4EC5\u4F7F\u7528\u4E24\u6B3E\u6E38\u620F\u5171\u6709\u7684 Hash \u6587\u672C\u8FDB\u884C\u5339\u914D\uFF0C\u9002\u5408\u9700\u8981\u53CC\u6E38\u620F\u901A\u7528\u7684 ID
                \u6CE8\uFF1A  ID \u90E8\u5206\u6700\u957F 16 \u4F4D\uFF1B\u4F7F\u7528 Hash \u4E0A\u4E0B\u754C\u526A\u679D\u52A0\u901F\u641C\u7D22
                Repo: https://github.com/LaoHuaJiOfficial/EAID_Generator, Credit to BV1yEmYYdEH3
                """);
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setMargin(new Insets(10, 10, 10, 10));
        frame.add(descriptionArea, BorderLayout.NORTH);
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        encodeBtn.addActionListener(e -> startComputation(currentGame));
        stopBtn.addActionListener(e -> {
            shouldStop.set(true);
            resultArea.setText("");
        });
        gameSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.label));
        gameSelector.addActionListener(e -> switchGame((Game) gameSelector.getSelectedItem()));
        switchGame(Game.BF1);
        row1.add(new JLabel("\u6E38\u620F:"));
        row1.add(gameSelector);
        row1.add(encodeBtn);
        row1.add(stopBtn);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("\u524D\u7F00:"));
        row2.add(prefixField);
        row2.add(new JLabel("EAID:"));
        row2.add(idField);
        row2.add(new JLabel("Hash:"));
        row2.add(hashField);
        row2.add(new JLabel("\u6570\u91CF\u4E0A\u9650:"));
        row2.add(limitField);
        row2.add(progressLabel);
        inputPanel.add(row1);
        inputPanel.add(row2);
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
    private static void switchGame(Game game) {
        currentGame = game;
        Map<Integer, String> textMap = TEXT_MAPS.get(game);
        StringBuilder sb = new StringBuilder(textMap.size() * 32);
        textMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append(String.format("%08X", entry.getKey()))
                        .append(':')
                        .append(entry.getValue())
                        .append('\n'));
        localization.setText(sb.toString());
        resultArea.setText("");
        progressLabel.setText("\u5DF2\u5207\u6362\u81F3 " + game.label + "\uFF0C\u5171 " + textMap.size() + " \u6761\u6587\u672C");
    }
    private static void startComputation(Game game) {
        if (isRunning) return;
        isRunning = true;
        encodeBtn.setText("\u5904\u7406\u4E2D");
        shouldStop.set(false);
        resultArea.setText("");
        long hash;
        try {
            hash = Long.parseUnsignedLong(hashField.getText().trim(), 16);
        } catch (Exception ex) {
            resultArea.append("\nHash \u8F93\u5165\u6709\u8BEF: " + ex.getMessage());
            isRunning = false;
            encodeBtn.setText("\u8BA1\u7B97");
            return;
        }
        int maxResults;
        try {
            maxResults = parseMaxResults(limitField.getText());
        } catch (IllegalArgumentException ex) {
            resultArea.append("\n\u6570\u91CF\u4E0A\u9650\u8F93\u5165\u6709\u8BEF: " + ex.getMessage());
            isRunning = false;
            encodeBtn.setText("\u8BA1\u7B97");
            return;
        }
        Map<Integer, String> textMap = TEXT_MAPS.get(game);
        int[] sortedKeys = SORTED_KEYS.get(game);
        String pattern = idField.getText();
        String bracketPrefix;
        try {
            bracketPrefix = parseBracketPrefix(prefixField.getText());
        } catch (IllegalArgumentException ex) {
            resultArea.append("\n\u524D\u7F00\u8F93\u5165\u6709\u8BEF: " + ex.getMessage());
            isRunning = false;
            encodeBtn.setText("\u8BA1\u7B97");
            return;
        }
        decodeSingleTail(bracketPrefix, pattern, hash, textMap, sortedKeys, maxResults);
    }
    private static int parseMaxResults(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_RESULTS;
        }
        int value = Integer.parseInt(raw.trim());
        if (value <= 0) {
            throw new IllegalArgumentException("\u987B\u4E3A\u6B63\u6574\u6570");
        }
        return value;
    }
    private static String parseBracketPrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String prefix = raw.trim();
        if (prefix.length() < MIN_BRACKET_PREFIX_LEN || prefix.length() > MAX_BRACKET_PREFIX_LEN) {
            throw new IllegalArgumentException("\u524D\u7F00\u9700\u4E3A 3-4 \u4E2A\u5B57\u7B26\uFF0C\u6216\u7559\u7A7A");
        }
        return "[" + prefix + "]";
    }
    private static void decodeSingleTail(String bracketPrefix, String base, long hashValue,
                                         Map<Integer, String> textMap, int[] sortedKeys, int maxResults) {
        String idBase = base.replace("@", "").trim();
        if (idBase.isEmpty()) {
            resultArea.setText("\u8BF7\u586B\u5199 EAID \u524D\u7F00\n");
            finishComputation();
            return;
        }
        if (idBase.length() > MAX_SINGLE_ID_LEN) {
            resultArea.setText("EAID \u90E8\u5206\u4E0D\u80FD\u8D85\u8FC7 " + MAX_SINGLE_ID_LEN + " \u4F4D\n");
            finishComputation();
            return;
        }
        if (bracketPrefix.length() + idBase.length() > MAX_ENCODE_LEN) {
            resultArea.setText("\u524D\u7F00\u4E0E EAID \u5408\u8BA1\u8FC7\u957F\n");
            finishComputation();
            return;
        }
        int maxTailLen = Math.min(
                MAX_SINGLE_ID_LEN - idBase.length(),
                MAX_ENCODE_LEN - bracketPrefix.length() - idBase.length());
        new Thread(() -> {
            try {
                List<String> results = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (int tailLen = 0; tailLen <= maxTailLen && !shouldStop.get() && results.size() < maxResults; tailLen++) {
                    int currentTailLen = tailLen;
                    int found = results.size();
                    SwingUtilities.invokeLater(() ->
                            progressLabel.setText("\u5C1D\u8BD5\u5C3E\u90E8\u901A\u914D\u7B26\u957F\u5EA6: " + currentTailLen + "\uFF0C\u5DF2\u627E\u5230: " + found));
                    if (!tailLengthCanMatch(bracketPrefix, idBase, tailLen, hashValue, sortedKeys)) {
                        continue;
                    }
                    int remaining = maxResults - results.size();
                    searchBoundedMulti(bracketPrefix, idBase, tailLen, hashValue, textMap, sortedKeys,
                            results, seen, remaining, maxResults);
                }
                if (results.isEmpty() && !shouldStop.get()) {
                    resultArea.append("\n\u672A\u627E\u5230\u53EF\u884C ID \n");
                } else if (!bracketPrefix.isEmpty()) {
                    resultArea.append("\n\u8BA1\u7B97\u4F7F\u7528\u524D\u7F00: " + bracketPrefix + "\n");
                }
            } finally {
                finishComputation();
                resultArea.append("\n\u4EFB\u52A1\u5DF2\u5B8C\u6210\n");
            }
        }).start();
    }
    private static boolean tailLengthCanMatch(String bracketPrefix, String idBase, int tailLen,
                                              long hashValue, int[] sortedKeys) {
        String placeholder = bracketPrefix + idBase + "!".repeat(tailLen);
        long baseHash = encode(placeholder);
        if (tailLen == 0) {
            long totalHash = baseHash % MOD;
            if (hashValue == 0) {
                return Arrays.binarySearch(sortedKeys, (int) totalHash) >= 0;
            }
            return totalHash == hashValue;
        }
        long minExtra = 0;
        long maxExtra = 0;
        for (int i = 0; i < tailLen; i++) {
            long weight = WEIGHT[tailLen - i - 1];
            minExtra += weight * MIN_CHAR_OFFSET;
            maxExtra += weight * MAX_CHAR_OFFSET;
        }
        long low = (baseHash + minExtra) % MOD;
        long high = (baseHash + maxExtra) % MOD;
        if (hashValue == 0) {
            return hasKeyInModularRange(sortedKeys, low, high);
        }
        return isInModularRange(hashValue, low, high);
    }
    private static void searchBoundedMulti(String bracketPrefix, String idBase, int tailLen, long hashValue,
                                           Map<Integer, String> textMap, int[] sortedKeys,
                                           List<String> results, Set<String> seen, int maxAdd, int maxResults) {
        String pattern = bracketPrefix + idBase + "@".repeat(tailLen);
        if (pattern.length() > MAX_ENCODE_LEN) {
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
        int bracketLen = bracketPrefix.length();
        if (wildcardCount == 0) {
            long totalHash = baseHash % MOD;
            boolean hit = hashValue == 0
                    ? textMap.containsKey((int) totalHash)
                    : totalHash == hashValue;
            if (hit) {
                addSingleResult(bracketPrefix, idBase, hashValue, textMap, results, seen, maxAdd, maxResults);
            }
            return;
        }
        SearchContext context = buildSearchContext(size, wildcardIndices, wildcardCount);
        char[] attempt = newPattern.toCharArray();
        dfsSearchMulti(bracketPrefix, attempt, 0, 0L, baseHash, hashValue, textMap, sortedKeys, wildcardIndices,
                context, wildcardCount, bracketLen, results, seen, maxAdd, maxResults);
    }
    private static void dfsSearchMulti(String bracketPrefix, char[] attempt, int depth, long partialExtra, long baseHash,
                                       long hashValue, Map<Integer, String> textMap, int[] sortedKeys,
                                       List<Integer> wildcardIndices, SearchContext context, int wildcardCount,
                                       int bracketLen, List<String> results, Set<String> seen, int maxAdd,
                                       int maxResults) {
        if (shouldStop.get() || results.size() >= maxResults || maxAdd <= 0) {
            return;
        }
        if (depth == wildcardCount) {
            long totalHash = (baseHash + partialExtra) % MOD;
            boolean hit = hashValue == 0
                    ? textMap.containsKey((int) totalHash)
                    : totalHash == hashValue;
            if (hit) {
                String idPart = new String(attempt, bracketLen, attempt.length - bracketLen);
                addSingleResult(bracketPrefix, idPart, hashValue, textMap, results, seen, maxAdd, maxResults);
            }
            return;
        }
        long low = (baseHash + partialExtra + context.suffixMinLinear[depth]) % MOD;
        long high = (baseHash + partialExtra + context.suffixMaxLinear[depth]) % MOD;
        if (hashValue == 0) {
            if (!hasKeyInModularRange(sortedKeys, low, high)) {
                return;
            }
        } else if (!isInModularRange(hashValue, low, high)) {
            return;
        }
        for (int charIndex = 0; charIndex < CHARSET_LEN; charIndex++) {
            if (shouldStop.get() || results.size() >= maxResults || maxAdd <= 0) {
                return;
            }
            attempt[wildcardIndices.get(depth)] = CHARSET[charIndex];
            long nextExtra = (partialExtra + context.contributions[depth][charIndex]) % MOD;
            dfsSearchMulti(bracketPrefix, attempt, depth + 1, nextExtra, baseHash, hashValue, textMap, sortedKeys,
                    wildcardIndices, context, wildcardCount, bracketLen, results, seen, maxAdd, maxResults);
        }
    }
    private static void addSingleResult(String bracketPrefix, String idPart, long hashValue,
                                        Map<Integer, String> textMap, List<String> results, Set<String> seen,
                                        int maxAdd, int maxResults) {
        if (results.size() >= maxResults || maxAdd <= 0) {
            return;
        }
        String fullId = bracketPrefix + idPart;
        long computedHash = encode(fullId);
        if (hashValue != 0) {
            if (computedHash != hashValue) {
                return;
            }
        } else if (!textMap.containsKey((int) computedHash)) {
            return;
        }
        if (!seen.add(fullId)) {
            return;
        }
        String name = textMap.getOrDefault((int) computedHash, "\u672A\u77E5");
        results.add(fullId);
        appendResult(fullId, computedHash, name);
    }
    private static SearchContext buildSearchContext(int size, List<Integer> wildcardIndices, int wildcardCount) {
        long[][] contributions = new long[wildcardCount][CHARSET_LEN];
        long[] suffixMinLinear = new long[wildcardCount + 1];
        long[] suffixMaxLinear = new long[wildcardCount + 1];
        for (int j = 0; j < wildcardCount; j++) {
            long weight = WEIGHT[size - wildcardIndices.get(j) - 1];
            for (int c = 0; c < CHARSET_LEN; c++) {
                contributions[j][c] = (weight * (CHARSET[c] - '!')) % MOD;
            }
            suffixMinLinear[j] = suffixMinLinear[j + 1] + weight * MIN_CHAR_OFFSET;
            suffixMaxLinear[j] = suffixMaxLinear[j + 1] + weight * MAX_CHAR_OFFSET;
        }
        return new SearchContext(contributions, suffixMinLinear, suffixMaxLinear);
    }
    private static boolean isInModularRange(long value, long low, long high) {
        if (low <= high) {
            return value >= low && value <= high;
        }
        return value >= low || value <= high;
    }
    private static boolean hasKeyInModularRange(int[] keys, long low, long high) {
        if (keys.length == 0) return false;
        if (low <= high) {
            int left = lowerBound(keys, (int) low);
            return left < keys.length && (keys[left] & 0xFFFFFFFFL) <= high;
        }
        return hasKeyInModularRange(keys, low, 0xFFFFFFFFL)
                || hasKeyInModularRange(keys, 0L, high);
    }
    private static int lowerBound(int[] keys, int target) {
        int left = 0;
        int right = keys.length;
        while (left < right) {
            int mid = (left + right) >>> 1;
            if (Integer.compareUnsigned(keys[mid], target) < 0) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        return left;
    }
    private static int[] buildSortedKeys(Map<Integer, String> textMap) {
        return textMap.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
    }
    private static void finishComputation() {
        isRunning = false;
        SwingUtilities.invokeLater(() -> encodeBtn.setText("\u8BA1\u7B97"));
    }
    private static void appendResult(String fullId, long computedHash, String name) {
        String line = "\n\u67E5\u8BE2 " + fullId
                + "  Hash\uFF1A" + String.format("%08X", computedHash)
                + "  \u4E2D\u6587ID\uFF1A" + name;
        SwingUtilities.invokeLater(() -> {
            resultArea.append(line);
            resultArea.setCaretPosition(resultArea.getDocument().getLength());
        });
    }
    public static long encode(String input) {
        long result = 0;
        for (int i = 0; i < input.length(); i++) {
            int value = input.charAt(i) - 32;
            result = ((result * 33) & 0xFFFFFFFFL) + value;
        }
        return (result - 1) & 0xFFFFFFFFL;
    }
    private static Map<Integer, String> loadBundle(Game game) {
        Map<Integer, String> textMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(Main.class.getResourceAsStream(game.resourcePath),
                        "\u672A\u627E\u5230\u8D44\u6E90\u6587\u4EF6: " + game.resourcePath),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = BUNDLE_LINE.matcher(line);
                if (matcher.matches()) {
                    textMap.put(Integer.parseUnsignedInt(matcher.group(1), 16), matcher.group(2));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("\u52A0\u8F7D " + game.label + " \u6587\u672C\u5931\u8D25", e);
        }
        return textMap;
    }
    public static List<String> generateFuzzyVariants(String input) {
        List<Integer> indices = new ArrayList<>();
        List<char[]> opts = new ArrayList<>();
        char[] base = input.toCharArray();
        for (int i = 0; i < base.length; i++) {
            if (FUZZY_MAP.containsKey(base[i])) {
                indices.add(i);
                opts.add(FUZZY_MAP.get(base[i]));
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
    private record SearchContext(long[][] contributions, long[] suffixMinLinear, long[] suffixMaxLinear) {
    }
}
