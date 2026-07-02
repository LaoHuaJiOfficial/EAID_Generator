importScripts('core.js');

let stopped = false;

self.onmessage = (e) => {
    const msg = e.data;
    if (msg.type === 'stop') {
        stopped = true;
        return;
    }
    if (msg.type !== 'search') {
        return;
    }
    stopped = false;

    const textMap = new Map(msg.textMapEntries);
    const sortedKeys = msg.sortedKeys;
    const { bracketPrefix, idBase, hashValue, maxResults } = msg;

    const result = EAIDCore.decodeSingleTail(
        bracketPrefix,
        idBase,
        hashValue,
        textMap,
        sortedKeys,
        maxResults,
        () => stopped,
        {
            onProgress(tailLen, found) {
                self.postMessage({ type: 'progress', tailLen, found });
            },
            onResult(item) {
                self.postMessage({ type: 'result', ...item });
            },
        }
    );

    if (result.error) {
        self.postMessage({ type: 'error', message: result.error });
    } else {
        self.postMessage({
            type: 'done',
            count: result.results.length,
            bracketPrefix: result.bracketPrefix,
            stopped,
        });
    }
};
