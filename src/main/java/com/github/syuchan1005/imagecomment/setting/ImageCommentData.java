package com.github.syuchan1005.imagecomment.setting;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "ImageCommentData", storages = {@Storage(value = "ImageCommentData.xml")})
public class ImageCommentData implements PersistentStateComponent<ImageCommentData> {
	public boolean isHover = true;
	public int hoverDelay = 700;

	@Nullable
	public static ImageCommentData getInstance() {
		return ServiceManager.getService(ImageCommentData.class);
	}

	@Nullable
	@Override
	public ImageCommentData getState() {
		return this;
	}

	@Override
	public void loadState(@NotNull ImageCommentData state) {
		XmlSerializerUtil.copyBean(state, this);
	}


}
