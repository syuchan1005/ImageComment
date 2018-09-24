package com.github.syuchan1005.imagecomment;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IconUtil;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.ImageUtil;
import java.awt.Image;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommentUtil {
	private static Pattern imageCommentPattern = Pattern.compile("!\\[(.*?)]\\((.*?)\\)");

	private static Map<URL, ImageData> imageDataCache = new HashMap<>();

	@NotNull
	public static List<MatchData> getMatchData(String text) {
		Matcher matcher = imageCommentPattern.matcher(text);
		List<MatchData> matches = new ArrayList<>();
		while (matcher.find()) {
			matches.add(new MatchData(matcher.group(), matcher.start()));
		}
		return matches;
	}

	@Nullable
	public static URL getAbsoluteURL(PsiElement element, String path) {
		Project project = element.getProject();
		Path joinedPath;
		if (path.startsWith("/")) {
			joinedPath = Paths.get(Objects.requireNonNull(project.getBasePath()), path);
		} else {
			String directoryPath = element.getContainingFile().getContainingDirectory().getVirtualFile().getPath();
			joinedPath = Paths.get(directoryPath, path);
		}
		try {
			return joinedPath.toUri().toURL();
		} catch (MalformedURLException ignored) {}
		return null;
	}

	public static ImageData loadImage(URL url) {
		if (!imageDataCache.containsKey(url)) {
			Image image = ImageLoader.loadFromUrl(url);
			if (image == null) return null;
			imageDataCache.put(url, new ImageData(image));
		}
		return imageDataCache.get(url);
	}

	public static class MatchData {
		public String text;
		public int startOffset;

		private String alt;
		private String path;

		private MatchData(String text, int startOffset) {
			this.text = text;
			this.startOffset = startOffset;
		}

		public String getAlt() {
			if (alt == null) {
				alt = text.substring(2, text.indexOf('(') - 1);
			}
			return alt;
		}

		public String getPath() {
			if (path == null) {
				path = text.substring(text.indexOf('(') + 1, text.length() - 1);
			}
			return path;
		}

		@Override
		public String toString() {
			return startOffset + ": " + text;
		}
	}

	public static class ImageData {
		private static double iconSize = 16;
		private static double thumbnailSize = 200;

		private Image image;

		private Icon icon;
		private Image thumbnail;


		public ImageData(@NotNull Image image) {
			this.image = image;
		}

		public Image getImage() {
			return image;
		}

		public Icon toIcon() {
			if (icon == null) {
				int max = Math.max(ImageUtil.getRealWidth(image), ImageUtil.getRealHeight(image));
				Image im = iconSize >= max ? image : ImageUtil.scaleImage(image, iconSize / max);
				icon = IconUtil.createImageIcon(im);
			}
			return icon;
		}

		public Image getThumbnail() {
			if (thumbnail == null) {
				int max = Math.max(ImageUtil.getRealWidth(image), ImageUtil.getRealHeight(image));
				thumbnail = thumbnailSize >= max ? image : ImageUtil.scaleImage(image, thumbnailSize / max);
			}
			return thumbnail;
		}
	}
}
