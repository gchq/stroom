/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.server;

import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.util.shared.HasDisplayValue;

public enum UserStatus implements HasDisplayValue, HasPrimitiveValue {
    ENABLED("Enabled", 0), // Normal User.
    DISABLED("Disabled", 1), // Old User no longer allowed access.
    LOCKED("Locked", 2), // Locked user due to sign in problems.
    EXPIRED("Expired", 3); // Expired account.

    public static final PrimitiveValueConverter<UserStatus> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<>(
            UserStatus.values());
    private final String displayValue;
    private final byte primitiveValue;

    UserStatus(final String displayValue, final int primitiveValue) {
        this.displayValue = displayValue;
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}