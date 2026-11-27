#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CI 覆盖率门禁：解析 JaCoCo CSV，按「核心类」粒度卡阈值。
用法：python3 check_coverage.py <jacoco.csv> <类名> <行覆盖阈值> <分支覆盖阈值>
未达阈值 → 退出码 1，流水线红灯。
"""
import csv
import sys


def pct(covered, missed):
    total = covered + missed
    return 100.0 if total == 0 else covered * 100.0 / total


def main():
    if len(sys.argv) < 3:
        print("usage: check_coverage.py <jacoco.csv> <class> [line_th=90] [branch_th=90]")
        return 2
    path, klass = sys.argv[1], sys.argv[2]
    line_th = float(sys.argv[3]) if len(sys.argv) > 3 else 90.0
    branch_th = float(sys.argv[4]) if len(sys.argv) > 4 else 90.0

    rows = list(csv.DictReader(open(path, encoding="utf-8")))
    target = [r for r in rows if r["CLASS"] == klass]
    if not target:
        print(f"[FAIL] 未在 {path} 中找到类 {klass}")
        return 1

    r = target[0]
    line = pct(int(r["LINE_COVERED"]), int(r["LINE_MISSED"]))
    branch = pct(int(r["BRANCH_COVERED"]), int(r["BRANCH_MISSED"]))
    inst = pct(int(r["INSTRUCTION_COVERED"]), int(r["INSTRUCTION_MISSED"]))
    print(f"[coverage] {klass}: line={line:.1f}% branch={branch:.1f}% instruction={inst:.1f}%")
    ok = line >= line_th and branch >= branch_th
    if not ok:
        print(f"[FAIL] 门禁未通过：要求 line>={line_th}% 且 branch>={branch_th}%")
        return 1
    print("[PASS] 覆盖率门禁通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
