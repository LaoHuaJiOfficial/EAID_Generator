const EAIDCore = (() => {
    const CHARSET = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_';
    const CHARSET_LEN = CHARSET.length;
    const MOD = 0x100000000n;
    const MAX_ENCODE_LEN = 22;
    const MAX_SINGLE_ID_LEN = 16;
    const DEFAULT_MAX_RESULTS = 10;
    const MIN_BRACKET_PREFIX_LEN = 3;
    const MAX_BRACKET_PREFIX_LEN = 4;
    const WEIGHT = [
        0x00000001, 0x00000021, 0x00000441, 0x00008C61,
        0x00121881, 0x025528A1, 0x4CFA3CC1, 0xEC41D4E1,
        0x747C7101, 0x040A9121, 0x855CB541, 0x30F35D61,
        0x4F5F0981, 0x3B4039A1, 0xA3476DC1, 0x0C3525E1,
        0x92D9E201, 0xEE162221, 0xB0DA6641, 0xCC272E61,
        0x510CFA81, 0x72AC4AA1,
    ];
    const BUNDLE_LINE = /^([0-9A-Fa-f]+),"(.*)"$/;

    let MIN_CHAR_OFFSET = Infinity;
    let MAX_CHAR_OFFSET = -Infinity;
    for (let i = 0; i < CHARSET_LEN; i++) {
        const offset = CHARSET.charCodeAt(i) - 33;
        MIN_CHAR_OFFSET = Math.min(MIN_CHAR_OFFSET, offset);
        MAX_CHAR_OFFSET = Math.max(MAX_CHAR_OFFSET, offset);
    }

    function u32(n) {
        return Number(BigInt(n) & 0xFFFFFFFFn);
    }

    function encode(input) {
        let result = 0n;
        for (let i = 0; i < input.length; i++) {
            const value = BigInt(input.charCodeAt(i) - 32);
            result = ((result * 33n) & 0xFFFFFFFFn) + value;
        }
        return u32(result - 1n);
    }

    function parseBundle(text) {
        const textMap = new Map();
        const lines = text.split(/\r?\n/);
        for (const line of lines) {
            const m = line.match(BUNDLE_LINE);
            if (m) {
                textMap.set(parseInt(m[1], 16), m[2]);
            }
        }
        return textMap;
    }

    function buildSortedKeys(textMap) {
        return Array.from(textMap.keys()).sort((a, b) => (a >>> 0) - (b >>> 0));
    }

    function formatLocalization(textMap) {
        const keys = buildSortedKeys(textMap);
        const parts = new Array(keys.length);
        for (let i = 0; i < keys.length; i++) {
            const k = keys[i];
            parts[i] = k.toString(16).toUpperCase().padStart(8, '0') + ':' + textMap.get(k);
        }
        return parts.join('\n');
    }

    function parseMaxResults(raw) {
        if (raw == null || raw.trim() === '') {
            return DEFAULT_MAX_RESULTS;
        }
        const value = parseInt(raw.trim(), 10);
        if (Number.isNaN(value) || value <= 0) {
            throw new Error('须为正整数');
        }
        return value;
    }

    function parseBracketPrefix(raw) {
        if (raw == null || raw.trim() === '') {
            return '';
        }
        const prefix = raw.trim();
        if (prefix.length < MIN_BRACKET_PREFIX_LEN || prefix.length > MAX_BRACKET_PREFIX_LEN) {
            throw new Error('前缀需为 3-4 个字符，或留空');
        }
        return '[' + prefix + ']';
    }

    function parseHash(raw) {
        const trimmed = raw.trim();
        if (!/^[0-9A-Fa-f]+$/.test(trimmed)) {
            throw new Error('无效的十六进制 Hash');
        }
        return parseInt(trimmed, 16) >>> 0;
    }

    function isInModularRange(value, low, high) {
        value = u32(value);
        low = u32(low);
        high = u32(high);
        if (low <= high) {
            return value >= low && value <= high;
        }
        return value >= low || value <= high;
    }

    function lowerBound(keys, target) {
        target = target >>> 0;
        let left = 0;
        let right = keys.length;
        while (left < right) {
            const mid = (left + right) >>> 1;
            if ((keys[mid] >>> 0) < target) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        return left;
    }

    function hasKeyInModularRange(keys, low, high) {
        if (keys.length === 0) return false;
        low = u32(low);
        high = u32(high);
        if (low <= high) {
            const idx = lowerBound(keys, low);
            return idx < keys.length && (keys[idx] >>> 0) <= high;
        }
        return hasKeyInModularRange(keys, low, 0xFFFFFFFF)
            || hasKeyInModularRange(keys, 0, high);
    }

    function buildSearchContext(size, wildcardIndices, wildcardCount) {
        const contributions = Array.from({ length: wildcardCount }, () => new Array(CHARSET_LEN));
        const suffixMinLinear = new Array(wildcardCount + 1).fill(0);
        const suffixMaxLinear = new Array(wildcardCount + 1).fill(0);
        for (let j = 0; j < wildcardCount; j++) {
            const weight = WEIGHT[size - wildcardIndices[j] - 1];
            for (let c = 0; c < CHARSET_LEN; c++) {
                contributions[j][c] = u32(BigInt(weight) * BigInt(CHARSET.charCodeAt(c) - 33));
            }
            suffixMinLinear[j] = suffixMinLinear[j + 1] + weight * MIN_CHAR_OFFSET;
            suffixMaxLinear[j] = suffixMaxLinear[j + 1] + weight * MAX_CHAR_OFFSET;
        }
        return { contributions, suffixMinLinear, suffixMaxLinear };
    }

    function tailLengthCanMatch(bracketPrefix, idBase, tailLen, hashValue, sortedKeys) {
        const placeholder = bracketPrefix + idBase + '!'.repeat(tailLen);
        const baseHash = encode(placeholder);
        if (tailLen === 0) {
            const totalHash = u32(baseHash);
            if (hashValue === 0) {
                return lowerBound(sortedKeys, totalHash) < sortedKeys.length
                    && sortedKeys[lowerBound(sortedKeys, totalHash)] === totalHash;
            }
            return totalHash === hashValue;
        }
        let minExtra = 0;
        let maxExtra = 0;
        for (let i = 0; i < tailLen; i++) {
            const weight = WEIGHT[tailLen - i - 1];
            minExtra += weight * MIN_CHAR_OFFSET;
            maxExtra += weight * MAX_CHAR_OFFSET;
        }
        const low = u32(baseHash + minExtra);
        const high = u32(baseHash + maxExtra);
        if (hashValue === 0) {
            return hasKeyInModularRange(sortedKeys, low, high);
        }
        return isInModularRange(hashValue, low, high);
    }

    function addSingleResult(bracketPrefix, idPart, hashValue, textMap, results, seen, maxAdd, maxResults, onResult) {
        if (results.length >= maxResults || maxAdd <= 0) {
            return;
        }
        const fullId = bracketPrefix + idPart;
        const computedHash = encode(fullId);
        if (hashValue !== 0) {
            if (computedHash !== hashValue) {
                return;
            }
        } else if (!textMap.has(computedHash)) {
            return;
        }
        if (seen.has(fullId)) {
            return;
        }
        seen.add(fullId);
        const name = textMap.get(computedHash) ?? '未知';
        results.push(fullId);
        onResult({ fullId, computedHash, name });
    }

    function dfsSearchMulti(bracketPrefix, attempt, depth, partialExtra, baseHash, hashValue, textMap,
                            sortedKeys, wildcardIndices, context, wildcardCount, bracketLen,
                            results, seen, maxAdd, maxResults, shouldStop, onResult) {
        if (shouldStop() || results.length >= maxResults || maxAdd <= 0) {
            return;
        }
        if (depth === wildcardCount) {
            const totalHash = u32(baseHash + partialExtra);
            const hit = hashValue === 0
                ? textMap.has(totalHash)
                : totalHash === hashValue;
            if (hit) {
                const idPart = attempt.slice(bracketLen).join('');
                addSingleResult(bracketPrefix, idPart, hashValue, textMap, results, seen,
                    maxAdd, maxResults, onResult);
            }
            return;
        }
        const low = u32(baseHash + partialExtra + context.suffixMinLinear[depth]);
        const high = u32(baseHash + partialExtra + context.suffixMaxLinear[depth]);
        if (hashValue === 0) {
            if (!hasKeyInModularRange(sortedKeys, low, high)) {
                return;
            }
        } else if (!isInModularRange(hashValue, low, high)) {
            return;
        }
        for (let charIndex = 0; charIndex < CHARSET_LEN; charIndex++) {
            if (shouldStop() || results.length >= maxResults || maxAdd <= 0) {
                return;
            }
            attempt[wildcardIndices[depth]] = CHARSET[charIndex];
            const nextExtra = u32(partialExtra + context.contributions[depth][charIndex]);
            dfsSearchMulti(bracketPrefix, attempt, depth + 1, nextExtra, baseHash, hashValue, textMap,
                sortedKeys, wildcardIndices, context, wildcardCount, bracketLen,
                results, seen, maxAdd, maxResults, shouldStop, onResult);
        }
    }

    function searchBoundedMulti(bracketPrefix, idBase, tailLen, hashValue, textMap, sortedKeys,
                                results, seen, maxAdd, maxResults, shouldStop, onResult) {
        const pattern = bracketPrefix + idBase + '@'.repeat(tailLen);
        if (pattern.length > MAX_ENCODE_LEN) {
            return;
        }
        const size = pattern.length;
        const newPattern = pattern.replace(/@/g, '!');
        const baseHash = encode(newPattern);
        const wildcardIndices = [];
        for (let i = 0; i < newPattern.length; i++) {
            if (newPattern.charAt(i) === '!') {
                wildcardIndices.push(i);
            }
        }
        const wildcardCount = wildcardIndices.length;
        const bracketLen = bracketPrefix.length;
        if (wildcardCount === 0) {
            const totalHash = u32(baseHash);
            const hit = hashValue === 0
                ? textMap.has(totalHash)
                : totalHash === hashValue;
            if (hit) {
                addSingleResult(bracketPrefix, idBase, hashValue, textMap, results, seen,
                    maxAdd, maxResults, onResult);
            }
            return;
        }
        const context = buildSearchContext(size, wildcardIndices, wildcardCount);
        const attempt = newPattern.split('');
        dfsSearchMulti(bracketPrefix, attempt, 0, 0, baseHash, hashValue, textMap, sortedKeys,
            wildcardIndices, context, wildcardCount, bracketLen, results, seen, maxAdd, maxResults,
            shouldStop, onResult);
    }

    function decodeSingleTail(bracketPrefix, base, hashValue, textMap, sortedKeys, maxResults, shouldStop, callbacks) {
        const idBase = base.replace(/@/g, '').trim();
        if (idBase === '') {
            return { error: '请填写 EAID 前缀' };
        }
        if (idBase.length > MAX_SINGLE_ID_LEN) {
            return { error: 'EAID 部分不能超过 ' + MAX_SINGLE_ID_LEN + ' 位' };
        }
        if (bracketPrefix.length + idBase.length > MAX_ENCODE_LEN) {
            return { error: '前缀与 EAID 合计过长' };
        }
        const maxTailLen = Math.min(
            MAX_SINGLE_ID_LEN - idBase.length,
            MAX_ENCODE_LEN - bracketPrefix.length - idBase.length
        );
        const results = [];
        const seen = new Set();
        for (let tailLen = 0; tailLen <= maxTailLen && !shouldStop() && results.length < maxResults; tailLen++) {
            callbacks.onProgress(tailLen, results.length);
            if (!tailLengthCanMatch(bracketPrefix, idBase, tailLen, hashValue, sortedKeys)) {
                continue;
            }
            const remaining = maxResults - results.length;
            searchBoundedMulti(bracketPrefix, idBase, tailLen, hashValue, textMap, sortedKeys,
                results, seen, remaining, maxResults, shouldStop, callbacks.onResult);
        }
        return { results, bracketPrefix };
    }

    return {
        CHARSET,
        DEFAULT_MAX_RESULTS,
        encode,
        parseBundle,
        buildSortedKeys,
        formatLocalization,
        parseMaxResults,
        parseBracketPrefix,
        parseHash,
        decodeSingleTail,
    };
})();

if (typeof module !== 'undefined' && module.exports) {
    module.exports = EAIDCore;
}
