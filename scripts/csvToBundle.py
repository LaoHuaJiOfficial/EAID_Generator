#!/usr/bin/env python3
import re
import sys
from pathlib import Path

LINE_PATTERN = re.compile(r'^([0-9A-Fa-f]+),"(.*)"$')
ROOT = Path(__file__).resolve().parent.parent
DEFAULT_INPUT = ROOT / 'battlefield4.csv'
OUTPUTS = [
    ROOT / 'src' / 'BF4Bundle.txt',
    ROOT / 'resources' / 'BF4Bundle.txt',
]


def escape_text(text: str) -> str:
    return text.replace('\\', '\\\\').replace('"', '\\"')


def convert(input_path: Path, output_paths: list[Path]) -> int:
    lines: list[str] = []
    seen: set[int] = set()
    skipped = 0

    with input_path.open(encoding='utf-8') as source:
        for raw in source:
            line = raw.rstrip('\r\n')
            if not line:
                continue
            match = LINE_PATTERN.match(line)
            if not match:
                skipped += 1
                continue
            key = int(match.group(1), 16)
            if key in seen:
                skipped += 1
                continue
            seen.add(key)
            text = escape_text(match.group(2))
            lines.append(f'{key:08X},"{text}"')

    content = '\r\n'.join(lines) + '\r\n'
    for output_path in output_paths:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(content, encoding='utf-8', newline='')

    print(f'Converted {len(lines)} entries from {input_path.name}')
    if skipped:
        print(f'Skipped {skipped} invalid or duplicate lines')
    for output_path in output_paths:
        print(f'Written: {output_path}')
    return len(lines)


if __name__ == '__main__':
    input_file = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_INPUT
    if not input_file.exists():
        print(f'Input file not found: {input_file}', file=sys.stderr)
        sys.exit(1)
    convert(input_file, OUTPUTS)
