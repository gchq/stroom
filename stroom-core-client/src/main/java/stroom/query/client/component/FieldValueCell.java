/*
 * Copyright 2026 Crown Copyright
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

package stroom.query.client.component;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.client.TickBoxState;
import stroom.query.shared.FieldValueCount;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.function.Function;
import java.util.function.Predicate;

/// A value in the value picker ([FieldValuePicker]), named BEM style (`field-value-cell.css`): a
/// tick box when several values can be picked, the value, a bar showing the share of records that
/// have it, and that share as a percentage. A value that can't be picked is faded.
public class FieldValueCell extends AbstractCell<FieldValueCount> {

    private static final TickBoxCell.DefaultAppearance TICK_BOX = new TickBoxCell.DefaultAppearance();
    private static final NumberFormat PERCENT_FORMAT = NumberFormat.getFormat("0.0");

    private final Function<FieldValueCount, TickBoxState> tickState;
    private final Predicate<FieldValueCount> pickable;
    private final Function<FieldValueCount, Double> percentOf;

    /// @param tickState Gives a value's tick, or null when values aren't ticked.
    /// @param pickable  Whether a value can be picked.
    /// @param percentOf Gives the share of records that have a value, from 0 to 100.
    public FieldValueCell(final Function<FieldValueCount, TickBoxState> tickState,
                          final Predicate<FieldValueCount> pickable,
                          final Function<FieldValueCount, Double> percentOf) {
        this.tickState = tickState;
        this.pickable = pickable;
        this.percentOf = percentOf;
    }

    @Override
    public void render(final Context context, final FieldValueCount item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final double percent = percentOf.apply(item);
            sb.appendHtmlConstant(pickable.test(item)
                    ? "<div class=\"field-value-cell\">"
                    // A value that can't be picked, e.g. one holding a comma for an 'in' term
                    : "<div class=\"field-value-cell field-value-cell--unpickable\">");

            // The tick box as a picture: the list's option says whether it is ticked (aria-checked)
            final TickBoxState state = tickState.apply(item);
            if (state != null) {
                sb.append(TICK_BOX.getPicture(state));
            }

            sb.appendHtmlConstant("<div class=\"field-value-cell__text\">");
            sb.appendEscaped(item.getValue());
            sb.appendHtmlConstant("</div>");

            // The bar's fill is as wide as the share, which field-value-cell.css takes from the
            // custom property. Only drawn: the percentage after it says the same
            sb.appendHtmlConstant("<div class=\"field-value-cell__bar\" aria-hidden=\"true\">"
                                  + "<div class=\"field-value-cell__bar-fill\" style=\"--field-value-cell__percent: "
                                  // A plain number, whatever the locale's decimal separator
                                  + (Math.round(percent * 10) / 10D)
                                  + "%\"></div></div>");

            sb.appendHtmlConstant("<div class=\"field-value-cell__percent\">");
            sb.appendEscaped(PERCENT_FORMAT.format(percent) + "%");
            sb.appendHtmlConstant("</div>");

            sb.appendHtmlConstant("</div>");
        }
    }
}
