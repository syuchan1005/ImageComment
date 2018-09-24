package com.github.syuchan1005.imagecomment;

import com.github.syuchan1005.imagecomment.setting.ImageCommentData;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.ide.PowerSaveMode;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.Alarm;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MouseHoverHandler implements ProjectComponent {
	private enum BrowseMode {None, Hover}

	private TooltipProvider myTooltipProvider = null;

	private final DocumentationManager myDocumentationManager;

	@Nullable
	private Point myPrevMouseLocation;

	private LightweightHint myHint;

	private Project myProject;

	private ImageCommentData myData;

	@NotNull
	private final Alarm myDocAlarm;
	@NotNull
	private final Alarm myTooltipAlarm;

	private final EditorMouseListener myEditorMouseListener = new EditorMouseListener() {
		@Override
		public void mouseReleased(@NotNull EditorMouseEvent e) {
			myTooltipAlarm.cancelAllRequests();
			myTooltipProvider = null;
		}
	};

	private final EditorMouseMotionListener myEditorMouseMotionListener = new EditorMouseMotionListener() {
		@Override
		public void mouseMoved(@NotNull EditorMouseEvent e) {
			if (myHint != null) {
				HintManager.getInstance().hideAllHints();
				myHint = null;
			}

			if (e.isConsumed() || !myProject.isInitialized()) {
				return;
			}
			MouseEvent mouseEvent = e.getMouseEvent();

			if (isMouseOverTooltip(mouseEvent.getLocationOnScreen())
					|| ScreenUtil.isMovementTowards(myPrevMouseLocation, mouseEvent.getLocationOnScreen(), getHintBounds())) {
				myPrevMouseLocation = mouseEvent.getLocationOnScreen();
				return;
			}
			myPrevMouseLocation = mouseEvent.getLocationOnScreen();

			Editor editor = e.getEditor();
			if (editor.getProject() != null && editor.getProject() != myProject) return;

			Point point = new Point(mouseEvent.getPoint());
			final LogicalPosition pos = editor.xyToLogicalPosition(point);
			int offset = editor.logicalPositionToOffset(pos);
			int selStart = editor.getSelectionModel().getSelectionStart();
			int selEnd = editor.getSelectionModel().getSelectionEnd();

			int myStoredModifiers = mouseEvent.getModifiers();
			final BrowseMode browseMode = myStoredModifiers == 0 ? BrowseMode.Hover : BrowseMode.None;

			if (myTooltipProvider != null) {
				myTooltipProvider.dispose();
			}

			if (browseMode == BrowseMode.None || offset >= selStart && offset < selEnd) {
				myTooltipAlarm.cancelAllRequests();
				myTooltipProvider = null;
				return;
			}

			myTooltipAlarm.cancelAllRequests();
			final Editor finalEditor = editor;
			myTooltipAlarm.addRequest(() -> {
				if (HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) return;
				myTooltipProvider = new TooltipProvider(finalEditor, pos);
				myTooltipProvider.execute(browseMode);
			}, myData.hoverDelay);
		}
	};

	public MouseHoverHandler(final Project project, StartupManager startupManager,
							 @NotNull DocumentationManager documentationManager,
							 @NotNull final EditorFactory editorFactory) {
		myProject = project;
		myData = ImageCommentData.getInstance();
		assert myData != null;
		startupManager.registerPostStartupActivity(new DumbAwareRunnable() {
			@Override
			public void run() {
				EditorEventMulticaster eventMulticaster = editorFactory.getEventMulticaster();
				eventMulticaster.addEditorMouseListener(myEditorMouseListener, project);
				eventMulticaster.addEditorMouseMotionListener(myEditorMouseMotionListener, project);
				eventMulticaster.addCaretListener(new CaretListener() {
					@Override
					public void caretPositionChanged(@NotNull CaretEvent e) {
						if (myHint != null) {
							myDocumentationManager.updateToolwindowContext();
						}
					}
				}, project);
			}
		});
		myDocumentationManager = documentationManager;
		myDocAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myProject);
		myTooltipAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myProject);
	}

	@Override
	@NotNull
	public String getComponentName() {
		return "MouseHoverHandler";
	}

	private boolean isMouseOverTooltip(@NotNull Point mouseLocationOnScreen) {
		Rectangle bounds = getHintBounds();
		return bounds != null && bounds.contains(mouseLocationOnScreen);
	}

	@Nullable
	private Rectangle getHintBounds() {
		LightweightHint hint = myHint;
		if (hint == null) {
			return null;
		}
		JComponent hintComponent = hint.getComponent();
		if (hintComponent == null || !hintComponent.isShowing()) {
			return null;
		}
		return new Rectangle(hintComponent.getLocationOnScreen(), hintComponent.getSize());
	}

	private abstract static class Info {
		@NotNull
		final PsiElement myElementAtPointer;
		private final List<TextRange> myRanges;

		Info(@NotNull PsiElement elementAtPointer, List<TextRange> ranges) {
			myElementAtPointer = elementAtPointer;
			myRanges = ranges;
		}

		Info(@NotNull PsiElement elementAtPointer) {
			this(elementAtPointer, Collections.singletonList(new TextRange(elementAtPointer.getTextOffset(),
					elementAtPointer.getTextOffset() + elementAtPointer.getTextLength())));
		}

		List<TextRange> getRanges() {
			return myRanges;
		}

		@NotNull
		public abstract DocInfo getInfo();

		public abstract boolean isValid(Document document);

		public abstract void showDocInfo(@NotNull DocumentationManager docManager);

		boolean rangesAreCorrect(Document document) {
			final TextRange docRange = new TextRange(0, document.getTextLength());
			for (TextRange range : getRanges()) {
				if (!docRange.contains(range)) return false;
			}

			return true;
		}
	}

	private static void showDumbModeNotification(final Project project) {
		DumbService.getInstance(project).showDumbModeNotification("Element information is not available during index update");
	}

	private static class InfoSingle extends Info {
		private final String result;

		InfoSingle(@NotNull PsiElement elementAtPointer, String result) {
			super(elementAtPointer);
			this.result = result;
		}

		@Override
		@NotNull
		public DocInfo getInfo() {
			try {
				return ReadAction.compute((ThrowableComputable<DocInfo, IndexNotReadyException>) () ->
						result == null ? DocInfo.EMPTY : new DocInfo(result, null, myElementAtPointer)
				);
			} catch (IndexNotReadyException e) {
				showDumbModeNotification(myElementAtPointer.getProject());
				return DocInfo.EMPTY;
			}
		}

		@Override
		public boolean isValid(Document document) {
			return myElementAtPointer.isValid() && rangesAreCorrect(document);
		}

		@Override
		public void showDocInfo(@NotNull DocumentationManager docManager) {
			docManager.setAllowContentUpdateFromContext(false);
		}
	}

	@Nullable
	private Info getInfoAt(final Editor editor, PsiFile file, int offset, BrowseMode browseMode) {
		if (browseMode == BrowseMode.Hover) {
			if (PowerSaveMode.isEnabled() || !myData.isHover) return null;
			final PsiElement elementAtPointer = file.findElementAt(offset);
			if (elementAtPointer == null) return null;
			if (!elementAtPointer.getNode().getElementType().toString().toUpperCase().endsWith("COMMENT")) return null;

			int cursorOffset = offset - elementAtPointer.getTextOffset();
			List<CommentUtil.MatchData> matchDataList = CommentUtil.getMatchData(elementAtPointer.getText());
			CommentUtil.MatchData matchData = null;
			for (CommentUtil.MatchData data : matchDataList) {
				int i = cursorOffset - data.startOffset;
				if (0 <= i && i <= data.text.length()) {
					matchData = data;
					break;
				}
			}
			if (matchData == null) return null;

			return new InfoSingle(elementAtPointer, matchData.getPath());
		}

		return null;
	}

	private class TooltipProvider {
		private final Editor myEditor;
		private final LogicalPosition myPosition;
		private BrowseMode myBrowseMode;
		private boolean myDisposed;

		public TooltipProvider(Editor editor, LogicalPosition pos) {
			myEditor = editor;
			myPosition = pos;
		}

		public void dispose() {
			myDisposed = true;
		}

		public void execute(BrowseMode browseMode) {
			if (myEditor.isDisposed()) return;

			myBrowseMode = browseMode;

			Document document = myEditor.getDocument();
			final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
			if (file == null) return;

			if (myEditor.isDisposed() || EditorUtil.inVirtualSpace(myEditor, myPosition)) {
				return;
			}

			final int offset = myEditor.logicalPositionToOffset(myPosition);

			int selStart = myEditor.getSelectionModel().getSelectionStart();
			int selEnd = myEditor.getSelectionModel().getSelectionEnd();

			if (offset >= selStart && offset < selEnd) return;
			ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
				@Override
				public void run() {
					ProgressIndicatorUtils.scheduleWithWriteActionPriority(new ReadTask() {
						@Override
						public void computeInReadAction(@NotNull ProgressIndicator indicator) {
							doExecute(file, offset);
						}

						@Override
						public void onCanceled(@NotNull ProgressIndicator indicator) {
						}
					});
				}
			});
		}

		private void doExecute(PsiFile file, int offset) {
			final Info info;
			try {
				info = getInfoAt(myEditor, file, offset, myBrowseMode);
			} catch (IndexNotReadyException e) {
				showDumbModeNotification(myProject);
				return;
			}
			if (info == null) return;

			ApplicationManager.getApplication().invokeLater(new Runnable() {
				@Override
				public void run() {
					if (myDisposed || myEditor.isDisposed() ||
							!myEditor.getComponent().isShowing() || !info.isValid(myEditor.getDocument())) return;

					DocInfo docInfo = info.getInfo();
					if (docInfo.text == null) return;

					if (myDocumentationManager.hasActiveDockedDocWindow()) {
						info.showDocInfo(myDocumentationManager);
					}

					if (docInfo.documentationAnchor == null) return;
					URL absoluteURL = CommentUtil.getAbsoluteURL(docInfo.documentationAnchor, docInfo.text);
					CommentUtil.ImageData imageData = CommentUtil.loadImage(absoluteURL);
					if (imageData == null) return;

					LightweightHint hint = new LightweightHint(new JLabel(new ImageIcon(imageData.getThumbnail())));
					myHint = hint;
					hint.addHintListener(event -> myHint = null);
					myDocAlarm.cancelAllRequests();

					HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
					Point p = HintManagerImpl.getHintPosition(hint, myEditor, myPosition, HintManager.ABOVE);
					hintManager.showEditorHint(hint, myEditor, p,
							HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING,
							0, false, HintManagerImpl.createHintHint(myEditor, p, hint, HintManager.ABOVE).setContentActive(false));
				}
			});
		}

	}

	private static class DocInfo {

		public static final DocInfo EMPTY = new DocInfo(null, null, null);

		@Nullable
		public final String text;
		@Nullable
		public final DocumentationProvider docProvider;
		@Nullable
		public final PsiElement documentationAnchor;

		DocInfo(@Nullable String text, @Nullable DocumentationProvider provider, @Nullable PsiElement documentationAnchor) {
			this.text = text;
			docProvider = provider;
			this.documentationAnchor = documentationAnchor;
		}
	}
}
