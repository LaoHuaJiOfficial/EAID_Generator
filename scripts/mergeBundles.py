#!/usr/bin/env python3
import re
import sys
from pathlib import Path

LINE_PATTERN = re.compile(r'^([0-9A-Fa-f]+),"(.*)"$')
ROOT = Path(__file__).resolve().parent.parent
DEFAULT_BF1 = ROOT / 'src' / 'BF1Bundle.txt'
DEFAULT_BF4 = ROOT / 'src' / 'BF4Bundle.txt'
OUTPUTS = [
    ROOT / 'src' / 'BF1&4Bundle.txt',
    ROOT / 'resources' / 'BF1&4Bundle.txt',
]


def load_bundle(path: Path) -> dict[int, str]:
    entries: dict[int, str] = {}
    with path.open(encoding='utf-8') as source:
        for raw in source:
            line = raw.rstrip('\r\n')
            if not line:
                continue
            match = LINE_PATTERN.match(line)
            if not match:
                continue
            key = int(match.group(1), 16)
            entries[key] = match.group(2)
    return entries


def escape_text(text: str) -> str:
    return text.replace('\\', '\\\\').replace('"', '\\"')


def merge_text(bf1_text: str, bf4_text: str) -> str:
    if bf1_text == bf4_text:
        return bf1_text
    return f'[BF1]{bf1_text} | [BF4]{bf4_text}'


def merge(bf1_path: Path, bf4_path: Path, output_paths: list[Path]) -> int:
    bf1 = load_bundle(bf1_path)
    bf4 = load_bundle(bf4_path)
    common_keys = sorted(set(bf1) & set(bf4))
    same_text = sum(1 for key in common_keys if bf1[key] == bf4[key])

    lines = []
    for key in common_keys:
        text = merge_text(bf1[key], bf4[key])
        lines.append(f'{key:08X},"{escape_text(text)}"')

    content = '\r\n'.join(lines) + '\r\n'
    for output_path in output_paths:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(content, encoding='utf-8', newline='')

    print(f'BF1 entries: {len(bf1)}')
    print(f'BF4 entries: {len(bf4)}')
    print(f'Common keys: {len(common_keys)}')
    print(f'Same text: {same_text}, different text: {len(common_keys) - same_text}')
    for output_path in output_paths:
        print(f'Written: {output_path}')
    return len(common_keys)


if __name__ == '__main__':
    bf1_file = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_BF1
    bf4_file = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_BF4
    if not bf1_file.exists():
        print(f'BF1 bundle not found: {bf1_file}', file=sys.stderr)
        sys.exit(1)
    if not bf4_file.exists():
        print(f'BF4 bundle not found: {bf4_file}', file=sys.stderr)
        sys.exit(1)
    merge(bf1_file, bf4_file, OUTPUTS)
