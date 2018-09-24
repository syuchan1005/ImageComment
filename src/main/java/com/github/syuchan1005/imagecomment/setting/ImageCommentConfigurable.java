package com.github.syuchan1005.imagecomment.setting;

import com.intellij.openapi.options.SearchableConfigurable;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImageCommentConfigurable implements SearchableConfigurable {
	private JPanel rootPane;
	private JCheckBox showHoverCheckBox;
	private JTextField tooltipDelayField;

	private ImageCommentData data;

	public ImageCommentConfigurable() {
		this.data = ImageCommentData.getInstance();
	}

	@NotNull
	@Override
	public String getId() {
		return "preference.imagecomment.ImageCommentConfigurable";
	}

	@Nls(capitalization = Nls.Capitalization.Title)
	@Override
	public String getDisplayName() {
		return "ImageComment";
	}

	@Nullable
	@Override
	public JComponent createComponent() {
		showHoverCheckBox.setSelected(data.isHover);
		tooltipDelayField.setText(String.valueOf(data.hoverDelay));
		return rootPane;
	}

	@Override
	public boolean isModified() {
		return showHoverCheckBox.isSelected() != data.isHover ||
				Integer.parseInt(tooltipDelayField.getText()) != data.hoverDelay;
	}

	@Override
	public void apply() {
		data.isHover = showHoverCheckBox.isSelected();
		data.hoverDelay = Integer.parseInt(tooltipDelayField.getText());
	}
}
