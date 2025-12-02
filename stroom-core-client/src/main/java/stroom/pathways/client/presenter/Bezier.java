package stroom.pathways.client.presenter;

import stroom.widget.htree.client.treelayout.Point;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;

public class Bezier {

    public static void quadratic(final HtmlBuilder hb, final Point start, final Point end) {
        final Point midPoint = new Point(
                ((end.getX() - start.getX()) / 2) + start.getX(),
                ((end.getY() - start.getY()) / 2) + start.getY());
        final Point controlPoint = new Point(midPoint.getX(), start.getY());

        hb.elem(SafeHtmlUtil.from("path"),
                new Attribute("d",
                        "M " + start.getX() + " " + start.getY() +
                        " Q " + controlPoint.getX() + " " + controlPoint.getY() +
                        ", " + midPoint.getX() + " " + midPoint.getY() +
                        " T " + end.getX() + " " + end.getY()));
    }

    public static void curve(final HtmlBuilder hb, final Point start, final Point end) {
        hb.elem(SafeHtmlUtil.from("path"),
                new Attribute("d",
                        "M " + start.getX() + " " + start.getY() +
                        " C " + start.getX() + " " + end.getY() +
                        ", " + start.getX() + " " + end.getY() +
                        ", " + end.getX() + " " + end.getY()));
    }
}
