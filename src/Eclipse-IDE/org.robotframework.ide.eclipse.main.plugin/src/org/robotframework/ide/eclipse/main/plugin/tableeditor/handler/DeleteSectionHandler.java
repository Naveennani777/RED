package org.robotframework.ide.eclipse.main.plugin.tableeditor.handler;

import java.util.Arrays;
import java.util.List;

import javax.inject.Named;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.tools.compat.parts.DIHandler;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISources;
import org.robotframework.ide.eclipse.main.plugin.model.RobotElement;
import org.robotframework.ide.eclipse.main.plugin.model.RobotSuiteFileSection;
import org.robotframework.ide.eclipse.main.plugin.model.cmd.DeleteSectionCommand;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.ISectionEditorPart;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.RobotEditorCommandsStack;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.RobotFormEditor;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.handler.DeleteSectionHandler.E4DeleteSection;

import com.google.common.base.Optional;

public class DeleteSectionHandler extends DIHandler<E4DeleteSection> {

    public DeleteSectionHandler() {
        super(E4DeleteSection.class);
    }

    public static class E4DeleteSection {

        @Execute
        public Object deleteSectionFromActivePage(@Named(ISources.ACTIVE_EDITOR_NAME) final RobotFormEditor editor,
                final RobotEditorCommandsStack stack) {
            final IEditorPart activeEditor = editor.getActiveEditor();

            if (activeEditor instanceof ISectionEditorPart) {
                final ISectionEditorPart part = (ISectionEditorPart) activeEditor;
                final Optional<RobotElement> section = part.provideSection(editor.provideSuiteModel());
                if (section.isPresent()) {
                    final List<RobotSuiteFileSection> sectionsToRemove = Arrays.asList((RobotSuiteFileSection)section.get());
                    stack.execute(new DeleteSectionCommand(sectionsToRemove));
                }
            }
            return null;
        }
    }
}
