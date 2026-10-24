package com.minigit.storage.diff;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Myers O(ND) line-based diff.
 */
public class MyersDiff implements DiffAlgorithm {

    @Override
    public List<DiffLine> diff(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int max = n + m;

        if (max == 0) {
            return new ArrayList<>();
        }

        int offset = max;
        int[] v = new int[2 * max + 2];
        List<int[]> trace = new ArrayList<>();

        // Forward pass: find the shortest edit script length D
        outer:
        for (int d = 0; d <= max; d++) {
            trace.add(v.clone());
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    x = v[offset + k + 1];       // move down (insert)
                } else {
                    x = v[offset + k - 1] + 1;   // move right (delete)
                }
                int y = x - k;
                while (x < n && y < m && a.get(x).equals(b.get(y))) {
                    x++;
                    y++;
                }
                v[offset + k] = x;
                if (x >= n && y >= m) {
                    break outer;
                }
            }
        }

        // Backtrack to build the edit script
        LinkedList<DiffLine> result = new LinkedList<>();
        int x = n;
        int y = m;

        for (int d = trace.size() - 1; d >= 0; d--) {
            int[] vd = trace.get(d);
            int k = x - y;

            int prevK;
            if (k == -d || (k != d && vd[offset + k - 1] < vd[offset + k + 1])) {
                prevK = k + 1;
            } else {
                prevK = k - 1;
            }
            int prevX = vd[offset + prevK];
            int prevY = prevX - prevK;

            while (x > prevX && y > prevY) {
                x--;
                y--;
                result.addFirst(new DiffLine(DiffType.EQUAL, a.get(x)));
            }

            if (d > 0) {
                if (x == prevX) {
                    y--;
                    result.addFirst(new DiffLine(DiffType.INSERT, b.get(y)));
                } else {
                    x--;
                    result.addFirst(new DiffLine(DiffType.DELETE, a.get(x)));
                }
            }

            x = prevX;
            y = prevY;
        }

        return new ArrayList<>(result);
    }
}