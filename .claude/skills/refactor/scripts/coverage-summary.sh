#!/usr/bin/env bash
# JaCoCo CSV → 클래스별 줄 · 분기 커버리지와 합계
# 사용: bash coverage-summary.sh target/site/jacoco/jacoco.csv
csv="${1:-target/site/jacoco/jacoco.csv}"
awk -F, 'NR>1 && ($8+$9)>0 {
  branch = ($6+$7) ? sprintf("%.1f%% (%d/%d)", 100*$7/($6+$7), $7, $6+$7) : "-";
  printf "%-55s line %5.1f%% (%d/%d)  branch %s\n", $2"."$3, 100*$9/($8+$9), $9, $8+$9, branch
}
NR>1 { lines+=$9; linesTotal+=$8+$9; branches+=$7; branchesTotal+=$6+$7 }
END { printf "TOTAL line %.1f%% (%d/%d)  branch %.1f%% (%d/%d)\n", 100*lines/linesTotal, lines, linesTotal, 100*branches/branchesTotal, branches, branchesTotal }' "$csv"
