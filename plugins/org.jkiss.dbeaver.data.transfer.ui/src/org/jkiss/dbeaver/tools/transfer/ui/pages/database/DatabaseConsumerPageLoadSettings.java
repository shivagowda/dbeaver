/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2021 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.tools.transfer.ui.pages.database;

import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPReferentialIntegrityController;
import org.jkiss.dbeaver.model.sql.registry.SQLDialectDescriptor;
import org.jkiss.dbeaver.model.sql.registry.SQLDialectRegistry;
import org.jkiss.dbeaver.model.sql.registry.SQLInsertReplaceMethodDescriptor;
import org.jkiss.dbeaver.model.struct.DBSDataManipulator;
import org.jkiss.dbeaver.tools.transfer.database.DatabaseConsumerSettings;
import org.jkiss.dbeaver.tools.transfer.database.DatabaseMappingContainer;
import org.jkiss.dbeaver.tools.transfer.internal.DTMessages;
import org.jkiss.dbeaver.tools.transfer.ui.internal.DTUIMessages;
import org.jkiss.dbeaver.tools.transfer.ui.wizard.DataTransferWizard;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.dialogs.ActiveWizardPage;
import org.jkiss.dbeaver.utils.HelpUtils;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class DatabaseConsumerPageLoadSettings extends ActiveWizardPage<DataTransferWizard> {
    private static final Log log = Log.getLog(DatabaseConsumerPageLoadSettings.class);

    private final String HELP_TOPIC_REPLACE_METHOD = "Data-Import-and-Replace";

    private Button transferAutoGeneratedColumns;
    private Button truncateTargetTable;
    private Button disableReferentialIntegrity;
    private Combo onDuplicateKeyInsertMethods;
    private Group loadSettings;
    private String disableReferentialIntegrityCheckboxTooltip;
    private boolean isDisablingReferentialIntegritySupported;

    public DatabaseConsumerPageLoadSettings() {
    	super(DTUIMessages.database_consumer_wizard_name);
        setTitle(DTUIMessages.database_consumer_wizard_title);
        setDescription(DTUIMessages.database_consumer_wizard_description);
    }

    @Override
    public void createControl(Composite parent) {
        initializeDialogUnits(parent);

        Composite composite = UIUtils.createComposite(parent, 1);

        final DatabaseConsumerSettings settings = getSettings();

        {
            loadSettings = UIUtils.createControlGroup(composite, DTUIMessages.database_consumer_wizard_name, 2, GridData.FILL_HORIZONTAL, 0);

            transferAutoGeneratedColumns = UIUtils.createCheckbox(
                loadSettings,
                DTUIMessages.database_consumer_wizard_transfer_checkbox_label,
                DTUIMessages.database_consumer_wizard_transfer_checkbox_tooltip,
                settings.isTransferAutoGeneratedColumns(), 2);
            transferAutoGeneratedColumns.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    settings.setTransferAutoGeneratedColumns(transferAutoGeneratedColumns .getSelection());
                }
            });

            truncateTargetTable = UIUtils.createCheckbox(loadSettings, DTUIMessages.database_consumer_wizard_truncate_checkbox_label,
                    DTUIMessages.database_consumer_wizard_truncate_checkbox_description, settings.isTruncateBeforeLoad(), 2);
            truncateTargetTable.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    if (truncateTargetTable.getSelection() && !confirmDataTruncate()) {
                        truncateTargetTable.setSelection(false);
                        return;
                    }
                    settings.setTruncateBeforeLoad(truncateTargetTable.getSelection());
                }
            });

            loadUISettingsForDisableReferentialIntegrityCheckbox();
            settings.setDisableReferentialIntegrity(isDisablingReferentialIntegritySupported && settings.isDisableReferentialIntegrity());
            disableReferentialIntegrity = UIUtils.createCheckbox(
                    loadSettings,
                    DTUIMessages.database_consumer_wizard_disable_referential_integrity_label,
                    disableReferentialIntegrityCheckboxTooltip,
                    settings.isDisableReferentialIntegrity(),
                    2
            );
            disableReferentialIntegrity.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    settings.setDisableReferentialIntegrity(disableReferentialIntegrity.getSelection());
                }
            });
            disableReferentialIntegrity.setEnabled(isDisablingReferentialIntegritySupported);

            UIUtils.createControlLabel(loadSettings, DTUIMessages.database_consumer_wizard_on_duplicate_key_insert_method_text);
            onDuplicateKeyInsertMethods = new Combo(loadSettings, SWT.DROP_DOWN | SWT.READ_ONLY);
            onDuplicateKeyInsertMethods.setLayoutData(new GridData(GridData.FILL, GridData.VERTICAL_ALIGN_BEGINNING, true, false, 1, 1));

            Link urlLabel = UIUtils.createLink(loadSettings, "<a href=\"" + HelpUtils.getHelpExternalReference(HELP_TOPIC_REPLACE_METHOD) + "\">"
                    + DTUIMessages.database_consumer_wizard_link_label_replace_method_wiki + "</a>", new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    UIUtils.launchProgram(HelpUtils.getHelpExternalReference(HELP_TOPIC_REPLACE_METHOD));
                }
            });
            urlLabel.setLayoutData(new GridData(GridData.FILL, GridData.VERTICAL_ALIGN_BEGINNING, false, false, 2, 1));
        }

        {
            Group performanceSettings = UIUtils.createControlGroup(composite, DTUIMessages.database_consumer_wizard_performance_group_label, 4, GridData.FILL_HORIZONTAL, 0);

            final Button newConnectionCheckbox = UIUtils.createCheckbox(
                performanceSettings,
                DTMessages.data_transfer_wizard_output_checkbox_new_connection,
                null,
                settings.isOpenNewConnections(),
                4);
            newConnectionCheckbox.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    settings.setOpenNewConnections(newConnectionCheckbox.getSelection());
                }
            });

            final Button useTransactionsCheck = UIUtils.createCheckbox(performanceSettings, DTUIMessages.database_consumer_wizard_transactions_checkbox_label, null, settings.isUseTransactions(), 4);
            useTransactionsCheck.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    settings.setUseTransactions(useTransactionsCheck.getSelection());
                }
            });

            final Spinner commitAfterEdit = UIUtils.createLabelSpinner(performanceSettings, DTUIMessages.database_consumer_wizard_commit_spinner_label, settings.getCommitAfterRows(), 1, Integer.MAX_VALUE);
            commitAfterEdit.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    settings.setCommitAfterRows(commitAfterEdit.getSelection());
                }
            });
            commitAfterEdit.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING, GridData.VERTICAL_ALIGN_BEGINNING, false, false, 3, 1));

            final Button useMultiRowInsert = UIUtils.createCheckbox(performanceSettings, DTUIMessages.database_consumer_wizard_checkbox_multi_insert_label, DTUIMessages.database_consumer_wizard_checkbox_multi_insert_description, settings.isUseMultiRowInsert(), 4);
            useMultiRowInsert.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    settings.setUseMultiRowInsert(useMultiRowInsert.getSelection());
                }
            });

            final Spinner multiRowInsertBatch = UIUtils.createLabelSpinner(performanceSettings, DTUIMessages.database_consumer_wizard_spinner_multi_insert_batch_size, settings.getMultiRowInsertBatch(), 1, Integer.MAX_VALUE);
            multiRowInsertBatch.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    settings.setMultiRowInsertBatch(multiRowInsertBatch.getSelection());
                }
            });
            multiRowInsertBatch.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING, GridData.VERTICAL_ALIGN_BEGINNING, false, false, 3, 1));


            final Button useBatchCheck = UIUtils.createCheckbox(performanceSettings, DTUIMessages.database_consumer_wizard_disable_import_batches_label, DTUIMessages.database_consumer_wizard_disable_import_batches_description, settings.isDisableUsingBatches(), 4);
            useBatchCheck.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    settings.setDisableUsingBatches(useBatchCheck.getSelection());
                    if (useBatchCheck.getSelection()) {
                        useMultiRowInsert.setSelection(false);
                        useMultiRowInsert.setEnabled(false);
                        settings.setUseMultiRowInsert(false);
                    } else if (!useBatchCheck.getSelection() && !useMultiRowInsert.getEnabled()) {
                        useMultiRowInsert.setEnabled(true);
                    }
                }
            });
        }

        {
            Group generalSettings = UIUtils.createControlGroup(composite, DTUIMessages.database_consumer_wizard_general_group_label, 4, GridData.FILL_HORIZONTAL, 0);
            final Button showTableCheckbox = UIUtils.createCheckbox(generalSettings, DTUIMessages.database_consumer_wizard_table_checkbox_label, null, settings.isOpenTableOnFinish(), 4);
            showTableCheckbox.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    settings.setOpenTableOnFinish(showTableCheckbox.getSelection());
                }
            });
            final Button showFinalMessageCheckbox = UIUtils.createCheckbox(generalSettings, DTUIMessages.database_consumer_wizard_final_message_checkbox_label, null, getWizard().getSettings().isShowFinalMessage(), 4);
            showFinalMessageCheckbox.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    getWizard().getSettings().setShowFinalMessage(showFinalMessageCheckbox.getSelection());
                }
            });
        }

        setControl(composite);
    }

    private void loadUISettingsForDisableReferentialIntegrityCheckbox() {
        isDisablingReferentialIntegritySupported = false;
        disableReferentialIntegrityCheckboxTooltip = "";
        try {
            getWizard().getRunnableContext().run(false, false, monitor -> {
                Collection<String> statements = new LinkedHashSet<>();
                for (DatabaseMappingContainer mappingContainer : getSettings().getDataMappings().values()) {
                    if (!(mappingContainer.getTarget() instanceof DBPReferentialIntegrityController)) {
                        continue;
                    }
                    DBPReferentialIntegrityController controller = (DBPReferentialIntegrityController) mappingContainer.getTarget();
                    try {
                        if (controller.supportsChangingReferentialIntegrity(monitor)) {
                            isDisablingReferentialIntegritySupported = true;
                            statements.add(controller.getChangeReferentialIntegrityStatement(monitor, false));
                            statements.add(controller.getChangeReferentialIntegrityStatement(monitor, true));
                        }
                    } catch (DBException e) {
                        log.debug("Unexpected error when calculating UI options for 'Disable referential integrity' checkbox", e);
                    }
                }
                if (!statements.isEmpty()) {
                    StringJoiner tooltip = new StringJoiner(
                        System.lineSeparator(),
                        DTUIMessages.database_consumer_wizard_disable_referential_integrity_tip_start + System.lineSeparator(),
                        ""
                    );
                    statements.forEach(tooltip::add);
                    disableReferentialIntegrityCheckboxTooltip = tooltip.toString();
                }
            });
        } catch (InvocationTargetException e) {
            log.debug("Unexpected error", e.getTargetException());
        } catch (InterruptedException e) {
            //ignore
        }
    }

    private DatabaseConsumerSettings getSettings() {
        return getWizard().getPageSettings(this, DatabaseConsumerSettings.class);
    }

    @Override
    public void activatePage() {

        updatePageCompletion();

        UIUtils.asyncExec(this::loadSettings);
    }

    private void loadSettings() {
        DatabaseConsumerSettings settings = getSettings();
        if (settings.isTruncateBeforeLoad() && !confirmDataTruncate()) {
            truncateTargetTable.setSelection(false);
            settings.setTruncateBeforeLoad(false);
        }
        loadInsertMethods();
    }

    private boolean confirmDataTruncate() {
        Shell shell = getContainer().getShell();
        if (shell.isVisible() || getSettings().isTruncateBeforeLoad()) {
            String tableNames = getWizard().getSettings().getDataPipes().stream().map(pipe -> pipe.getConsumer() == null ? "" : pipe.getConsumer().getObjectName()).collect(Collectors.joining(","));
            String checkbox_question = NLS.bind(DTUIMessages.database_consumer_wizard_truncate_checkbox_question, tableNames);
            if (!UIUtils.confirmAction(shell, DTUIMessages.database_consumer_wizard_truncate_checkbox_title, checkbox_question))
            {
                return false;
            }
        }
        return true;
    }

    private void loadInsertMethods() {
        DatabaseConsumerSettings settings = getSettings();
        DBPDataSource dataSource = settings.getContainerNode().getDataSource();
        List<SQLInsertReplaceMethodDescriptor> insertMethodsDescriptors = null;
        if (dataSource != null) {
            SQLDialectDescriptor dialectDescriptor = SQLDialectRegistry.getInstance().getDialect(dataSource.getSQLDialect().getDialectId());
            insertMethodsDescriptors = dialectDescriptor.getSupportedInsertReplaceMethodsDescriptors();
        }

        onDuplicateKeyInsertMethods.add(DBSDataManipulator.INSERT_NONE_METHOD);
        if (!CommonUtils.isEmpty(insertMethodsDescriptors)) {
            boolean emptyButton = true;
            for (SQLInsertReplaceMethodDescriptor insertMethod : insertMethodsDescriptors) {
                onDuplicateKeyInsertMethods.add(insertMethod.getLabel());
                if (insertMethod.getId().equals(settings.getOnDuplicateKeyInsertMethodId())) {
                    onDuplicateKeyInsertMethods.setText(insertMethod.getLabel());
                    emptyButton = false;
                }
            }
            if (emptyButton) {
                onDuplicateKeyInsertMethods.setText(DBSDataManipulator.INSERT_NONE_METHOD);
                if (!CommonUtils.isEmpty(settings.getOnDuplicateKeyInsertMethodId())) {
                    // May be this setting was used for another database
                    settings.setOnDuplicateKeyInsertMethodId(null);
                }
            }
        } else {
            onDuplicateKeyInsertMethods.setText(DBSDataManipulator.INSERT_NONE_METHOD);
            onDuplicateKeyInsertMethods.setEnabled(false);
            Label descLabel = new Label(loadSettings, SWT.NONE);
            descLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING, GridData.VERTICAL_ALIGN_BEGINNING, false, false, 2, 1));
            descLabel.setText(DTUIMessages.database_consumer_wizard_label_replace_method_not_supported);
            if (!CommonUtils.isEmpty(settings.getOnDuplicateKeyInsertMethodId())) {
                // May be this setting was used for another database
                settings.setOnDuplicateKeyInsertMethodId(null);
            }
        }

        List<SQLInsertReplaceMethodDescriptor> finalInsertMethodsDescriptors = insertMethodsDescriptors;
        onDuplicateKeyInsertMethods.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int selIndex = onDuplicateKeyInsertMethods.getSelectionIndex();
                if (selIndex > 0 && !CommonUtils.isEmpty(finalInsertMethodsDescriptors)) {
                    SQLInsertReplaceMethodDescriptor methodDescriptor = finalInsertMethodsDescriptors.get(selIndex - 1);
                    settings.setOnDuplicateKeyInsertMethodId(methodDescriptor.getId());
                } else {
                    settings.setOnDuplicateKeyInsertMethodId(onDuplicateKeyInsertMethods.getText());
                }
            }
        });
    }

    @Override
    public void deactivatePage() {
        super.deactivatePage();
    }

    @Override
    protected boolean determinePageCompletion() {
        return true;
    }

}