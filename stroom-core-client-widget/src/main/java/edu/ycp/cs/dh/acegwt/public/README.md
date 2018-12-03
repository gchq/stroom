For Stroom to be able to display four different annotation icons in the left hand gutter the source for `ace.js` has been altered. Any future update to `ace.js` will need to incorporate this change.

The original `ace.js` lines 15705-15711:
```
            var type = annotation.type;
            if (type == "error")
                rowInfo.className = " ace_error";
            else if (type == "warning" && rowInfo.className != " ace_error")
                rowInfo.className = " ace_warning";
            else if (type == "info" && (!rowInfo.className))
                rowInfo.className = " ace_info";
```

The replacement needed to allow custom annotation class names:
```
            rowInfo.className = " " + annotation.type;
```

