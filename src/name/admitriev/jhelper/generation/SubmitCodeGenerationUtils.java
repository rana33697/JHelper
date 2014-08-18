package name.admitriev.jhelper.generation;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import name.admitriev.jhelper.Util;
import name.admitriev.jhelper.components.Configurator;
import name.admitriev.jhelper.exceptions.NotificationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class SubmitCodeGenerationUtils {
	private SubmitCodeGenerationUtils() {
	}

	/**
	 * Generates code for submission.
	 * Inlines all used code except standard library and puts it to output file from configuration
	 * @param project Project to get configuration from
	 */
	public static void generateSubmissionFile(Project project, @NotNull PsiFile inputFile) {

		if(!Util.isCppFile(inputFile)) {
			throw new NotificationException("Not a cpp file", "Only cpp files are currently supported");
		}

		if(project == null) {
			throw new NotificationException("No project found", "Are you in any project?");
		}

		String result = IncludesProcessor.process(inputFile);
		PsiFile psiOutputFile = getOutputFile(project);

		writeToFile(psiOutputFile, authorComment(project), result);

		removeUnusedCode(psiOutputFile);
	}

	@NotNull
	private static PsiFile getOutputFile(Project project) {
		Configurator configurator = project.getComponent(Configurator.class);
		Configurator.State configuration = configurator.getState();

		VirtualFile outputFile = project.getBaseDir().findFileByRelativePath(configuration.getOutputFile());
		if(outputFile == null) {
			throw new NotificationException("No output file found.", "You should configure output file to point to existing file");
		}

		PsiFile psiOutputFile = PsiManager.getInstance(project).findFile(outputFile);
		if(psiOutputFile == null) {
			throw new NotificationException("Couldn't open output file as PSI");
		}
		return psiOutputFile;
	}

	private static void writeToFile(PsiFile outputFile, final String... strings) {
		final Project project = outputFile.getProject();
		final Document document = PsiDocumentManager.getInstance(project).getDocument(outputFile);
		if(document == null) {
			throw new NotificationException("Couldn't open output file as document");
		}

		new WriteCommandAction.Simple<Object>(outputFile.getProject(), outputFile) {
			@Override
			public void run() {
				document.deleteString(0, document.getTextLength());
				for (String string : strings) {
					document.insertString(document.getTextLength() ,string);
				}
				FileDocumentManager.getInstance().saveDocument(document);
				PsiDocumentManager.getInstance(project).commitDocument(document);
			}
		}.execute();
	}

	private static String authorComment(Project project) {
		Configurator configurator = project.getComponent(Configurator.class);
		Configurator.State configuration = configurator.getState();

		return "/**\n" +
		       " * code generated by JHelper\n" +
		       " * @author " + configuration.getAuthor() + '\n' +
		       " */\n\n";
	}

	private static void removeUnusedCode(PsiFile file) {
		while (true) {
			final Collection<PsiElement> toDelete = new ArrayList<PsiElement>();
			Project project = file.getProject();
			SearchScope scope = new GlobalSearchScope.FilesScope(project, Collections.singletonList(file.getVirtualFile()));
			file.acceptChildren(new DeletionMarkingVisitor(toDelete, scope));
			if(toDelete.isEmpty()) {
				break;
			}
			new WriteCommandAction.Simple<Object>(project, file) {
				@Override
				public void run() {
					for (PsiElement element : toDelete) {
						element.delete();
					}
				}
			}.execute();
		}
	}
}
