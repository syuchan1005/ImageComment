package com.github.syuchan1005.imagecomment;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImageLineMarkerProvider implements LineMarkerProvider {
	@Nullable
	@Override
	public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
		return null;
	}

	@Override
	public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
		elements.forEach(element -> {
			String typeName = element.getNode().getElementType().toString();
			if (!typeName.toUpperCase().endsWith("COMMENT")) return;

			CommentUtil.getMatchData(element.getText()).forEach(match -> {
				URL url = CommentUtil.getAbsoluteURL(element, match.getPath());
				assert url != null;

				int startOffset = element.getTextRange().getStartOffset() + match.startOffset;
				CommentUtil.ImageData imageData = CommentUtil.loadImage(url);
				if (imageData == null) return;
				LineMarkerInfo lineMarkerInfo = new LineMarkerInfo<>(element,
						new TextRange(startOffset, startOffset + match.text.length()),
						imageData.toIcon(), Pass.LINE_MARKERS,
						null, null, GutterIconRenderer.Alignment.CENTER);

				result.add(lineMarkerInfo);
			});
		});
	}


}
