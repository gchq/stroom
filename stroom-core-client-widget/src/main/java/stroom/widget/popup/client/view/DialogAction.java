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

package stroom.widget.popup.client.view;

public class DialogAction {
    private final DialogActionType type;
    private final boolean applyToFiltered;

    public DialogAction(final DialogActionType type, final boolean applyToFiltered) {
        this.type = type;
        this.applyToFiltered = applyToFiltered;
    }

    public static DialogAction.Builder builder() {
        return new DialogAction.Builder();
    }

    public DialogActionType getType() {
        return type;
    }

    public boolean isApplyToFiltered() {
        return applyToFiltered;
    }



    public static class Builder {
        private DialogActionType type;
        private boolean applyToFiltered;

        public Builder() {
            applyToFiltered = false;
        }

        public DialogAction.Builder type(final DialogActionType type) {
            this.type = type;
            return this;
        }

        public DialogAction.Builder applyToFiltered(final boolean applyToFiltered) {
            this.applyToFiltered = applyToFiltered;
            return this;
        }

        public DialogAction build() {
            return new DialogAction(
                    type,
                    applyToFiltered
            );
        }
    }
}
