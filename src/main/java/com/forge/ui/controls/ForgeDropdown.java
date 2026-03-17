package com.forge.ui.controls;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

/**
 * Styled ComboBox for the FORGE UI.
 *
 * Dark background, orange text, beveled border.
 * Delegates fully to ComboBox<T> — use it wherever a ComboBox would be used.
 */
public class ForgeDropdown<T> extends ComboBox<T> {

    private static final String STYLE =
        "-fx-background-color: #0a0a0a;" +
        "-fx-border-color: #333333;" +
        "-fx-border-width: 1px;" +
        "-fx-text-fill: #ff8800;" +
        "-fx-font-family: Monospace;" +
        "-fx-font-size: 11px;";

    private static final String CELL_STYLE =
        "-fx-background-color: #0a0a0a;" +
        "-fx-text-fill: #ff8800;" +
        "-fx-font-family: Monospace;" +
        "-fx-font-size: 11px;";

    // -----------------------------------------------------------------------

    public ForgeDropdown() {
        super();
        applyStyle();
    }

    public ForgeDropdown(ObservableList<T> items) {
        super(items);
        applyStyle();
    }

    // -----------------------------------------------------------------------

    private void applyStyle() {
        setStyle(STYLE);

        // Style the button cell (shown when closed)
        setButtonCell(makeCell());

        // Style popup cells
        setCellFactory(lv -> makeCell());
    }

    private ListCell<T> makeCell() {
        ListCell<T> cell = new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                }
                setStyle(CELL_STYLE);
            }
        };
        return cell;
    }
}
