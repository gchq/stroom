/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.xml.converter.ds3;

public class UniqueInt {
    private int[] arr;
    private int max = -1;

    public void add(final int val) {
        if (arr == null) {
            arr = new int[1];
            arr[0] = val;
            max = val;

        } else {
            boolean found = false;
            int i = 0;
            for (; i < arr.length; i++) {
                final int existing = arr[i];
                if (existing == val) {
                    found = true;
                    break;
                } else if (existing > val) {
                    break;
                }
            }

            if (!found) {
                // Store the max value.
                if (val > max) {
                    max = val;
                }

                // If we didn't find the value then we need to grow the array.
                final int[] tmp = new int[arr.length + 1];
                System.arraycopy(arr, 0, tmp, 0, i);
                System.arraycopy(arr, i, tmp, i + 1, arr.length - i);
                arr = tmp;
                arr[i] = val;
            }
        }
    }

    public int[] getArr() {
        return arr;
    }

    public int getMax() {
        return max;
    }

    @Override
    public String toString() {
        if (arr == null) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            sb.append(",");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }
}
