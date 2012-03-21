/*
 * Copyright (C) 2005-2012 ManyDesigns srl.  All rights reserved.
 * http://www.manydesigns.com/
 *
 * Unless you have purchased a commercial license agreement from ManyDesigns srl,
 * the following license terms apply:
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.manydesigns.portofino.pageactions.crud;

import com.manydesigns.elements.ElementsThreadLocals;
import com.manydesigns.elements.Mode;
import com.manydesigns.elements.annotations.*;
import com.manydesigns.elements.annotations.Label;
import com.manydesigns.elements.blobs.Blob;
import com.manydesigns.elements.blobs.BlobManager;
import com.manydesigns.elements.fields.*;
import com.manydesigns.elements.forms.FieldSet;
import com.manydesigns.elements.forms.*;
import com.manydesigns.elements.messages.SessionMessages;
import com.manydesigns.elements.options.DefaultSelectionProvider;
import com.manydesigns.elements.options.DisplayMode;
import com.manydesigns.elements.options.SelectionProvider;
import com.manydesigns.elements.reflection.ClassAccessor;
import com.manydesigns.elements.reflection.PropertyAccessor;
import com.manydesigns.elements.text.OgnlTextFormat;
import com.manydesigns.elements.text.QueryStringWithParameters;
import com.manydesigns.elements.util.MimeTypes;
import com.manydesigns.elements.xml.XhtmlBuffer;
import com.manydesigns.elements.xml.XmlBuffer;
import com.manydesigns.portofino.PortofinoProperties;
import com.manydesigns.portofino.application.QueryUtils;
import com.manydesigns.portofino.buttons.annotations.Button;
import com.manydesigns.portofino.buttons.annotations.Buttons;
import com.manydesigns.portofino.database.TableCriteria;
import com.manydesigns.portofino.dispatcher.PageInstance;
import com.manydesigns.portofino.logic.SelectionProviderLogic;
import com.manydesigns.portofino.model.database.*;
import com.manydesigns.portofino.navigation.ResultSetNavigation;
import com.manydesigns.portofino.pageactions.AbstractPageAction;
import com.manydesigns.portofino.pageactions.PageActionName;
import com.manydesigns.portofino.pageactions.annotations.ConfigurationClass;
import com.manydesigns.portofino.pageactions.annotations.ScriptTemplate;
import com.manydesigns.portofino.pageactions.annotations.SupportsDetail;
import com.manydesigns.portofino.pageactions.crud.configuration.CrudConfiguration;
import com.manydesigns.portofino.pageactions.crud.configuration.CrudProperty;
import com.manydesigns.portofino.pageactions.crud.configuration.SelectionProviderReference;
import com.manydesigns.portofino.pageactions.crud.reflection.CrudAccessor;
import com.manydesigns.portofino.reflection.TableAccessor;
import com.manydesigns.portofino.security.AccessLevel;
import com.manydesigns.portofino.security.RequiresPermissions;
import com.manydesigns.portofino.security.SupportsPermissions;
import com.manydesigns.portofino.servlets.DummyHttpServletRequest;
import com.manydesigns.portofino.stripes.NoCacheStreamingResolution;
import com.manydesigns.portofino.util.PkHelper;
import com.manydesigns.portofino.util.ShortNameUtils;
import jxl.Workbook;
import jxl.write.DateFormat;
import jxl.write.*;
import jxl.write.Number;
import jxl.write.biff.RowsExceededException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.util.UrlBuilder;
import ognl.OgnlContext;
import org.apache.commons.collections.MultiHashMap;
import org.apache.commons.collections.MultiMap;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONStringer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
* @author Paolo Predonzani     - paolo.predonzani@manydesigns.com
* @author Angelo Lupo          - angelo.lupo@manydesigns.com
* @author Giampiero Granatella - giampiero.granatella@manydesigns.com
* @author Alessio Stalla       - alessio.stalla@manydesigns.com
*/
@SupportsPermissions({ CrudAction.PERMISSION_CREATE, CrudAction.PERMISSION_EDIT, CrudAction.PERMISSION_DELETE })
@RequiresPermissions(level = AccessLevel.VIEW)
@ScriptTemplate("script_template.txt")
@ConfigurationClass(CrudConfiguration.class)
@SupportsDetail
@PageActionName("Crud")
public class CrudAction extends AbstractPageAction {
    public static final String copyright =
            "Copyright (c) 2005-2012, ManyDesigns srl";

    public final static String SEARCH_STRING_PARAM = "searchString";

    public CrudConfiguration crudConfiguration;

    public ClassAccessor classAccessor;
    public Table baseTable;
    public PkHelper pkHelper;
    public List<CrudSelectionProvider> crudSelectionProviders;
    public MultiMap availableSelectionProviders; //List<String> -> DatabaseSelectionProvider
    public String[] pk;
    public String propertyName;

    public final static String prefix = "";
    public final static String searchPrefix = prefix + "search_";

    //--------------------------------------------------------------------------
    // Web parameters
    //--------------------------------------------------------------------------

    public String[] selection;
    public String searchString;
    public String successReturnUrl;
    public Integer firstResult;
    public Integer maxResults;
    public String sortProperty;
    public String sortDirection;
    public boolean searchVisible;

    //Selection providers
    protected String relName;
    protected int selectionProviderIndex;
    protected String selectFieldMode;
    protected String labelSearch;

    //--------------------------------------------------------------------------
    // UI forms
    //--------------------------------------------------------------------------

    public SearchForm searchForm;
    public TableForm tableForm;
    public Form form;

    //--------------------------------------------------------------------------
    // Data objects
    //--------------------------------------------------------------------------

    public List objects;
    public Object object;
    public Session session;

    //**************************************************************************
    // Logging
    //**************************************************************************

    private static final Logger logger =
            LoggerFactory.getLogger(CrudAction.class);

    //**************************************************************************
    // Export
    //**************************************************************************
    private static final String TEMPLATE_FOP_SEARCH = "templateFOP-Search.xsl";
    private static final String TEMPLATE_FOP_READ = "templateFOP-Read.xsl";

    //**************************************************************************
    // Setup
    //**************************************************************************

    @Before
    public void prepare() {
        availableSelectionProviders = new MultiHashMap();
        if(crudConfiguration != null && crudConfiguration.getActualDatabase() != null) {
            crudSelectionProviders = new ArrayList<CrudSelectionProvider>();
            setupSelectionProviders();
        }
    }

    protected void setupSelectionProviders() {
        Set<String> configuredSPs = new HashSet<String>();
        for(SelectionProviderReference ref : crudConfiguration.getSelectionProviders()) {
            boolean added;
            if(ref.getForeignKey() != null) {
                added = setupSelectionProvider(ref, ref.getForeignKey(), configuredSPs);
            } else if(ref.getSelectionProvider() instanceof DatabaseSelectionProvider) {
                DatabaseSelectionProvider dsp = (DatabaseSelectionProvider) ref.getSelectionProvider();
                added = setupSelectionProvider(ref, dsp, configuredSPs);
            } else {
                logger.error("Unsupported selection provider: " + ref.getSelectionProvider());
                continue;
            }
            if(ref.isEnabled() && !added) {
                logger.warn("Selection provider {} not added; check whether the fields on which it is configured " +
                        "overlap with some other selection provider", ref);
            }
        }


        //Remove disabled selection providers and mark them as configured to avoid re-adding them
        Iterator<CrudSelectionProvider> it = crudSelectionProviders.iterator();
        while (it.hasNext()) {
            CrudSelectionProvider sp = it.next();
            if(sp.getSelectionProvider() == null) {
                it.remove();
                for(String fieldName : sp.getFieldNames()) {
                    configuredSPs.add(fieldName);
                }
            }
        }

        Table table = crudConfiguration.getActualTable();
        if(table != null) {
            for(ForeignKey fk : table.getForeignKeys()) {
                setupSelectionProvider(null, fk, configuredSPs);
            }
            for(ModelSelectionProvider dsp : table.getSelectionProviders()) {
                if(dsp instanceof DatabaseSelectionProvider) {
                    setupSelectionProvider(null, (DatabaseSelectionProvider) dsp, configuredSPs);
                } else {
                    logger.error("Unsupported selection provider: " + dsp);
                }
            }
        }
    }

    protected boolean setupSelectionProvider(
            @Nullable SelectionProviderReference ref,
            DatabaseSelectionProvider current,
            Set<String> configuredSPs) {
        List<Reference> references = current.getReferences();

        String[] fieldNames = new String[references.size()];
        Class[] fieldTypes = new Class[references.size()];

        int i = 0;
        for (Reference reference : references) {
            Column column = reference.getActualFromColumn();
            fieldNames[i] = column.getActualPropertyName();
            fieldTypes[i] = column.getActualJavaType();
            i++;
        }

        availableSelectionProviders.put(Arrays.asList(fieldNames), current);
        for(String fieldName : fieldNames) {
            //If another SP is configured for the same field, stop
            if(configuredSPs.contains(fieldName)) {
                return false;
            }
        }

        if(ref == null || ref.isEnabled()) {
            DisplayMode dm = ref != null ? ref.getDisplayMode() : DisplayMode.DROPDOWN;
            SelectionProvider selectionProvider = createSelectionProvider
                    (current, fieldNames, fieldTypes, dm);
            CrudSelectionProvider crudSelectionProvider =
                new CrudSelectionProvider(selectionProvider, fieldNames);
            crudSelectionProviders.add(crudSelectionProvider);
            Collections.addAll(configuredSPs, fieldNames);
            return true;
        } else {
            //To avoid automatically adding a FK later
            CrudSelectionProvider crudSelectionProvider =
                new CrudSelectionProvider(null, fieldNames);
            crudSelectionProviders.add(crudSelectionProvider);
            return false;
        }
    }

    protected SelectionProvider createSelectionProvider
            (DatabaseSelectionProvider current, String[] fieldNames,
             Class[] fieldTypes, DisplayMode dm) {
        DefaultSelectionProvider selectionProvider = null;
        String name = current.getName();
        String databaseName = current.getToDatabase();
        String sql = current.getSql();
        String hql = current.getHql();

        if (sql != null) {
            Session session = application.getSession(databaseName);
            Collection<Object[]> objects = QueryUtils.runSql(session, sql);
            selectionProvider = SelectionProviderLogic.createSelectionProvider(name, fieldNames.length, fieldTypes, objects);
            selectionProvider.setDisplayMode(dm);
        } else if (hql != null) {
            selectionProvider =
                    SelectionProviderLogic.createSelectionProviderFromHql(name, application, databaseName, hql, dm);

            if(current instanceof ForeignKey) {
                selectionProvider.sortByLabel();
            }
        } else {
            logger.warn("ModelSelection provider '{}':" +
                    " both 'hql' and 'sql' are null", name);
        }
        return selectionProvider;
    }

    //--------------------------------------------------------------------------
    // Crud operations
    //--------------------------------------------------------------------------

    @DefaultHandler
    public Resolution execute() {
        if (object == null) {
            return doSearch();
        } else {
            return read();
        }
    }

    //**************************************************************************
    // Search
    //**************************************************************************

    @Buttons({
        @Button(list = "crud-search-form", key = "commons.search", order = 1),
        @Button(list = "portlet-default-button", key = "commons.search")
    })
    public Resolution search() {
        //If embedded, search is always closed by default
        searchVisible = !isEmbedded();
        return doSearch();
    }

    protected Resolution doSearch() {
        if (classAccessor == null) {
            logger.debug("Crud not correctly configured");
            return forwardToPortletPage(PAGE_PORTLET_NOT_CONFIGURED);
        }

        try {
            setupSearchForm();
//            loadObjects();
            setupTableForm(Mode.VIEW);

            if(isEmbedded()) {
                return getEmbeddedSearchView();
            } else {
                cancelReturnUrl = new UrlBuilder(
                    context.getLocale(), dispatch.getAbsoluteOriginalPath(), false)
                    .toString();
                cancelReturnUrl = appendSearchStringParamIfNecessary(cancelReturnUrl);
                setupReturnToParentTarget();
                return getSearchView();
            }
        } catch(Exception e) {
            logger.warn("Crud not correctly configured", e);
            return forwardToPortletPage(PAGE_PORTLET_NOT_CONFIGURED);
        }
    }

    public Resolution jsonSearchData() throws JSONException, JSQLParserException {
        setupSearchForm();
        loadObjects();

        // calculate totalRecords
        TableCriteria criteria = new TableCriteria(baseTable);
        if(searchForm != null) {
            searchForm.configureCriteria(criteria);
        }
        QueryStringWithParameters query =
                QueryUtils.mergeQuery(crudConfiguration.getQuery(), criteria, this);

        String queryString = query.getQueryString();
        String totalRecordsQueryString = generateCountQuery(queryString);
        //TODO gestire count non disponibile (totalRecordsQueryString == null)
        List<Object> result = QueryUtils.runHqlQuery
                (session, totalRecordsQueryString,
                        query.getParameters());
        long totalRecords = (Long) result.get(0);

        setupTableForm(Mode.VIEW);
        JSONStringer js = new JSONStringer();
        js.object()
                .key("recordsReturned")
                .value(objects.size())
                .key("totalRecords")
                .value(totalRecords)
                .key("startIndex")
                .value(firstResult == null ? 0 : firstResult)
                .key("Result")
                .array();
        for (TableForm.Row row : tableForm.getRows()) {
            js.object()
                    .key("__rowKey")
                    .value(row.getKey());
            for (Field field : row) {
                Object value = field.getValue();
                String displayValue = field.getDisplayValue();
                String href = field.getHref();
                js.key(field.getPropertyAccessor().getName());
                js.object()
                        .key("value")
                        .value(value)
                        .key("displayValue")
                        .value(displayValue)
                        .key("href")
                        .value(href)
                        .endObject();
            }
            js.endObject();
        }
        js.endArray();
        js.endObject();
        String jsonText = js.toString();
        return new NoCacheStreamingResolution(MimeTypes.APPLICATION_JSON_UTF8, jsonText);
    }

    protected String generateCountQuery(String queryString) throws JSQLParserException {
        CCJSqlParserManager parserManager = new CCJSqlParserManager();
        try {
            PlainSelect plainSelect =
                (PlainSelect) ((Select) parserManager.parse(new StringReader(queryString))).getSelectBody();
            List items = plainSelect.getSelectItems();
            if(items.size() != 1) {
                logger.error("I don't know how to generate a count query for {}", queryString);
                return null;
            }
            SelectExpressionItem item = (SelectExpressionItem) items.get(0);
            Function function = new Function();
            function.setName("count");
            function.setParameters(new ExpressionList(Arrays.asList(item.getExpression())));
            item.setExpression(function);
            logger.debug("Query string {} contains select");
            return plainSelect.toString();
        } catch(Exception e) {
            logger.debug("Query string {} does not contain select");
            queryString = "SELECT count(*) " + queryString;
            PlainSelect plainSelect =
                (PlainSelect) ((Select) parserManager.parse(new StringReader(queryString))).getSelectBody();
            plainSelect.setOrderByElements(null);
            return plainSelect.toString();
        }
    }

    @Button(list = "crud-search-form", key = "commons.resetSearch", order = 2)
    public Resolution resetSearch() {
        return new RedirectResolution(dispatch.getOriginalPath()).addParameter("searchVisible", true);
    }

    //**************************************************************************
    // Read
    //**************************************************************************

    public Resolution read() {
        if(!crudConfiguration.isLargeResultSet()) {
            setupSearchForm(); // serve per la navigazione del result set
            loadObjects();
            setupPagination();
        }

        setupForm(Mode.VIEW);
        form.readFromObject(object);
        refreshBlobDownloadHref();

        cancelReturnUrl = new UrlBuilder(
                Locale.getDefault(), dispatch.getAbsoluteOriginalPath(), false)
                .toString();

        cancelReturnUrl = appendSearchStringParamIfNecessary(cancelReturnUrl);

        setupReturnToParentTarget();

        return getReadView();
    }

    protected void refreshBlobDownloadHref() {
        for (FieldSet fieldSet : form) {
            for (Field field : fieldSet) {
                if (field instanceof FileBlobField) {
                    FileBlobField fileBlobField = (FileBlobField) field;
                    Blob blob = fileBlobField.getValue();
                    if (blob != null) {
                        String url = getBlobDownloadUrl(
                                fileBlobField.getPropertyAccessor());
                        field.setHref(url);
                    }
                }
            }
        }
    }

    protected void refreshTableBlobDownloadHref() {
        Iterator<?> objIterator = objects.iterator();
        for (TableForm.Row row : tableForm.getRows()) {
            Iterator<Field> fieldIterator = row.iterator();
            Object obj = objIterator.next();
            String baseUrl = null;
            while (fieldIterator.hasNext()) {
                Field field = fieldIterator.next();
                if (field instanceof FileBlobField) {
                    if(baseUrl == null) {
                        String readLinkExpression = getReadLinkExpression();
                        String encoding = application.getPortofinoProperties().getString(PortofinoProperties.URL_ENCODING);
                        OgnlTextFormat hrefFormat =
                                OgnlTextFormat.create(readLinkExpression);
                        hrefFormat.setUrl(true);
                        hrefFormat.setEncoding(encoding);
                        baseUrl = hrefFormat.format(obj);
                    }

                    UrlBuilder urlBuilder = new UrlBuilder(Locale.getDefault(), baseUrl, false)
                        .addParameter("downloadBlob","")
                        .addParameter("propertyName", field.getPropertyAccessor().getName());

                    field.setHref(urlBuilder.toString());
                }
            }
        }
    }

    public String getBlobDownloadUrl(PropertyAccessor propertyAccessor) {
        UrlBuilder urlBuilder = new UrlBuilder(
                Locale.getDefault(), dispatch.getAbsoluteOriginalPath(), false)
                .addParameter("downloadBlob","")
                .addParameter("propertyName", propertyAccessor.getName());
        return urlBuilder.toString();
    }

    public Resolution downloadBlob() throws IOException, NoSuchFieldException {
        PropertyAccessor propertyAccessor =
                classAccessor.getProperty(propertyName);
        String code = (String) propertyAccessor.get(object);

        BlobManager blobManager = ElementsThreadLocals.getBlobManager();
        Blob blob = blobManager.loadBlob(code);
        long contentLength = blob.getSize();
        String contentType = blob.getContentType();
        InputStream inputStream = new FileInputStream(blob.getDataFile());
        String fileName = blob.getFilename();
        return new StreamingResolution(contentType, inputStream)
                .setFilename(fileName)
                .setLength(contentLength)
                .setLastModified(blob.getCreateTimestamp().getMillis());
    }



    //**************************************************************************
    // Create/Save
    //**************************************************************************

    @Button(list = "crud-search", key = "commons.create", order = 1)
    @RequiresPermissions(permissions = PERMISSION_CREATE)
    public Resolution create() {
        setupForm(Mode.CREATE);
        object = classAccessor.newInstance();
        createSetup(object);
        form.readFromObject(object);

        return getCreateView();
    }

    @Button(list = "crud-create", key = "commons.save", order = 1)
    @RequiresPermissions(permissions = PERMISSION_CREATE)
    public Resolution save() {
        setupForm(Mode.CREATE);
        object = classAccessor.newInstance();
        createSetup(object);
        form.readFromObject(object);

        form.readFromRequest(context.getRequest());
        if (form.validate()) {
            form.writeToObject(object);
            if(createValidate(object)) {
                try {
                    session.save(baseTable.getActualEntityName(), object);
                    createPostProcess(object);
                    session.getTransaction().commit();
                } catch(ConstraintViolationException e) {
                    SessionMessages.addErrorMessage(getMessage("crud.constraintViolation"));
                    return getCreateView();
                } catch (Throwable e) {
                    String rootCauseMessage = ExceptionUtils.getRootCauseMessage(e);
                    logger.warn(rootCauseMessage, e);
                    SessionMessages.addErrorMessage(rootCauseMessage);
                    return getCreateView();
                }
                pk = pkHelper.generatePkStringArray(object);
                String url = dispatch.getOriginalPath() + "/" + getPkForUrl(pk);
                XhtmlBuffer buffer = new XhtmlBuffer();
                buffer.write(getMessage("commons.save.successful") + ". ");
                String createUrl = dispatch.getAbsoluteOriginalPath();
                if(!createUrl.contains("?")) {
                    createUrl += "?";
                } else {
                    createUrl += "&";
                }
                createUrl += "create=";
                createUrl = appendSearchStringParamIfNecessary(createUrl);
                buffer.writeAnchor(createUrl, getMessage("commons.create.another"));
                SessionMessages.addInfoMessage(buffer);
                return new RedirectResolution(url);
            }
        }

        return getCreateView();
    }

    protected String getPkForUrl(String[] pk) {
        String encoding = application.getPortofinoProperties().getString(PortofinoProperties.URL_ENCODING);
        try {
            return pkHelper.getPkStringForUrl(pk, encoding);
        } catch (UnsupportedEncodingException e) {
            throw new Error(e); //TODO
        }
    }

    //**************************************************************************
    // Edit/Update
    //**************************************************************************

    @Button(list = "crud-read", key = "commons.edit", order = 1)
    @RequiresPermissions(permissions = PERMISSION_EDIT)
    public Resolution edit() {
        setupForm(Mode.EDIT);
        editSetup(object);
        form.readFromObject(object);
        return getEditView();
    }

    @Button(list = "crud-edit", key = "commons.update", order = 1)
    @RequiresPermissions(permissions = PERMISSION_EDIT)
    public Resolution update() {
        setupForm(Mode.EDIT);
        editSetup(object);
        form.readFromObject(object);
        form.readFromRequest(context.getRequest());
        if (form.validate()) {
            form.writeToObject(object);
            if(editValidate(object)) {
                try {
                    session.update(baseTable.getActualEntityName(), object);
                    editPostProcess(object);
                    session.getTransaction().commit();
                } catch(ConstraintViolationException e) {
                    SessionMessages.addErrorMessage(getMessage("crud.constraintViolation"));
                    return getEditView();
                } catch (Throwable e) {
                    String rootCauseMessage = ExceptionUtils.getRootCauseMessage(e);
                    logger.warn(rootCauseMessage, e);
                    SessionMessages.addErrorMessage(rootCauseMessage);
                    return getEditView();
                }
                SessionMessages.addInfoMessage(getMessage("commons.update.successful"));
                return new RedirectResolution(
                        appendSearchStringParamIfNecessary(dispatch.getOriginalPath()));
            }
        }
        return getEditView();
    }

    //**************************************************************************
    // Bulk Edit/Update
    //**************************************************************************

    @Button(list = "crud-search", key = "commons.edit", order = 2)
    @RequiresPermissions(permissions = PERMISSION_EDIT)
    public Resolution bulkEdit() {
        if (selection == null || selection.length == 0) {
            SessionMessages.addWarningMessage(getMessage("commons.bulkUpdate.nothingSelected"));
            return new RedirectResolution(cancelReturnUrl, false);
        }

        if (selection.length == 1) {
            pk = selection[0].split("/");
            String url = dispatch.getOriginalPath() + "/" + getPkForUrl(pk);
            return new RedirectResolution(url)
                    .addParameter("cancelReturnUrl", cancelReturnUrl)
                    .addParameter("edit");
        }

        setupForm(Mode.BULK_EDIT);

        return getBulkEditView();
    }

    @Button(list = "crud-bulk-edit", key = "commons.update", order = 1)
    @RequiresPermissions(permissions = PERMISSION_EDIT)
    public Resolution bulkUpdate() {
        setupForm(Mode.BULK_EDIT);
        form.readFromRequest(context.getRequest());
        if (form.validate()) {
            for (String current : selection) {
                loadObject(current.split("/"));
                editSetup(object);
                form.writeToObject(object);
                if(editValidate(object)) {
                    session.update(baseTable.getActualEntityName(), object);
                }
                editPostProcess(object);
            }
            try {
                session.getTransaction().commit();
            } catch (Throwable e) {
                String rootCauseMessage = ExceptionUtils.getRootCauseMessage(e);
                logger.warn(rootCauseMessage, e);
                SessionMessages.addErrorMessage(rootCauseMessage);
                return new ForwardResolution("/layouts/crud/bulk-edit.jsp");
            }
            SessionMessages.addInfoMessage(getMessage(
                    "commons.bulkUpdate.successful",
                    selection.length));
            return new RedirectResolution(
                    appendSearchStringParamIfNecessary(dispatch.getOriginalPath()));
        } else {
            return new ForwardResolution("/layouts/crud/bulk-edit.jsp");
        }
    }

    //**************************************************************************
    // Delete
    //**************************************************************************

    @Button(list = "crud-read", key = "commons.delete", order = 2)
    @RequiresPermissions(permissions = PERMISSION_DELETE)
    public Resolution delete() {
        String url = calculateBaseSearchUrl();
        if(deleteValidate(object)) {
            session.delete(baseTable.getActualEntityName(), object);
            try {
                deletePostProcess(object);
                session.getTransaction().commit();
                SessionMessages.addInfoMessage(getMessage("commons.delete.successful"));

                // invalidate the pk on this crud unit
                pk = null;
            } catch (Exception e) {
                String rootCauseMessage = ExceptionUtils.getRootCauseMessage(e);
                logger.debug(rootCauseMessage, e);
                SessionMessages.addErrorMessage(rootCauseMessage);
            }
        }
        return new RedirectResolution(appendSearchStringParamIfNecessary(url), false);
    }

    @Button(list = "crud-search", key = "commons.delete", order = 3)
    @RequiresPermissions(permissions = PERMISSION_DELETE)
    public Resolution bulkDelete() {
        int deleted = 0;
        if (selection == null) {
            SessionMessages.addWarningMessage(getMessage("commons.bulkDelete.nothingSelected"));
            return new RedirectResolution(appendSearchStringParamIfNecessary(dispatch.getOriginalPath()));
        }
        for (String current : selection) {
            String[] pkArr = current.split("/");
            Serializable pkObject = pkHelper.getPrimaryKey(pkArr);
            Object obj = QueryUtils.getObjectByPk(application, baseTable, pkObject);
            if(deleteValidate(obj)) {
                session.delete(baseTable.getActualEntityName(), obj);
                deletePostProcess(obj);
                deleted++;
            }
        }
        try {
            session.getTransaction().commit();
            SessionMessages.addInfoMessage(getMessage("commons.bulkDelete.successful", deleted));
        } catch (Exception e) {
            logger.warn(ExceptionUtils.getRootCauseMessage(e), e);
            SessionMessages.addErrorMessage(ExceptionUtils.getRootCauseMessage(e));
        }

        return new RedirectResolution(appendSearchStringParamIfNecessary(dispatch.getOriginalPath()));
    }

    //**************************************************************************
    // Permissions
    //**************************************************************************

    public static final String PERMISSION_CREATE = "crud-create";
    public static final String PERMISSION_EDIT = "crud-edit";
    public static final String PERMISSION_DELETE = "crud-delete";

    //**************************************************************************
    // Return to parent
    //**************************************************************************

    @Override
    public String getDescription() {
        if(pageInstance.getParameters().isEmpty()) {
            return crudConfiguration.getSearchTitle();
        } else {
            return ShortNameUtils.getName(classAccessor, object);
        }
    }

    @Override
    public void setupReturnToParentTarget() {
        if(!StringUtils.isBlank(searchString)) {
            returnToParentParams.put(SEARCH_STRING_PARAM, searchString);
        }
        if (pk != null) {
            returnToParentTarget = "search";
        } else {
            super.setupReturnToParentTarget();
        }
    }

    public Resolution returnToParent() throws Exception {
        RedirectResolution resolution;
        if (pk != null) {
            resolution = new RedirectResolution(
                    appendSearchStringParamIfNecessary(calculateBaseSearchUrl()), false);
        } else {
            PageInstance[] pageInstancePath =
                    dispatch.getPageInstancePath();
            int previousPos = pageInstancePath.length - 2;
            if (previousPos >= 0) {
                PageInstance previousPageInstance = pageInstancePath[previousPos];
                String url = previousPageInstance.getPath();
                resolution = new RedirectResolution(url, true);
            } else {
                resolution = new RedirectResolution(
                        appendSearchStringParamIfNecessary(calculateBaseSearchUrl()), false);
            }
        }

        return resolution;
    }

    @Override
    @Buttons({
        @Button(list = "crud-edit", key = "commons.cancel", order = 99),
        @Button(list = "crud-create", key = "commons.cancel", order = 99),
        @Button(list = "crud-bulk-edit", key = "commons.cancel", order = 99),
        @Button(list = "configuration", key = "commons.cancel", order = 99)
    })
    public Resolution cancel() {
        return super.cancel();
    }

    //**************************************************************************
    // Ajax
    //**************************************************************************

    public Resolution jsonSelectFieldOptions() {
        return jsonOptions(prefix, true);
    }

    public Resolution jsonSelectFieldSearchOptions() {
        return jsonOptions(searchPrefix, true);
    }

    public Resolution jsonAutocompleteOptions() {
        return jsonOptions(prefix, false);
    }

    public Resolution jsonAutocompleteSearchOptions() {
        return jsonOptions(searchPrefix, false);
    }

    protected Resolution jsonOptions(String prefix, boolean includeSelectPrompt) {
        CrudSelectionProvider crudSelectionProvider = null;
        for (CrudSelectionProvider current : crudSelectionProviders) {
            SelectionProvider selectionProvider =
                    current.getSelectionProvider();
            if (selectionProvider.getName().equals(relName)) {
                crudSelectionProvider = current;
                break;
            }
        }
        if (crudSelectionProvider == null) {
            return new ErrorResolution(500);
        }

        SelectionProvider selectionProvider =
                crudSelectionProvider.getSelectionProvider();
        String[] fieldNames = crudSelectionProvider.getFieldNames();

        Form form = new FormBuilder(classAccessor)
                .configFields(fieldNames)
                .configSelectionProvider(selectionProvider, fieldNames)
                .configPrefix(prefix)
                .configMode(Mode.EDIT)
                .build();

        FieldSet fieldSet = form.get(0);
        //Ensure the value is actually read from the request
        for(Field field : fieldSet) {
            field.setUpdatable(true);
        }
        form.readFromRequest(context.getRequest());

        SelectField targetField =
                (SelectField) fieldSet.get(selectionProviderIndex);
        targetField.setLabelSearch(labelSearch);

        String text = targetField.jsonSelectFieldOptions(includeSelectPrompt);
        logger.debug("jsonOptions: {}", text);
        return new NoCacheStreamingResolution(MimeTypes.APPLICATION_JSON_UTF8, text);
    }


    //**************************************************************************
    // Setup methods
    //**************************************************************************

    protected void setupPagination() {
        resultSetNavigation = new ResultSetNavigation();
        int position = objects.indexOf(object);
        int size = objects.size();
        resultSetNavigation.setPosition(position);
        resultSetNavigation.setSize(size);
        String baseUrl = calculateBaseSearchUrl();
        if(position >= 0) {
            if(position > 0) {
                resultSetNavigation.setFirstUrl(generateObjectUrl(baseUrl, 0));
                resultSetNavigation.setPreviousUrl(
                        generateObjectUrl(baseUrl, position - 1));
            }
            if(position < size - 1) {
                resultSetNavigation.setLastUrl(
                        generateObjectUrl(baseUrl, size - 1));
                resultSetNavigation.setNextUrl(
                        generateObjectUrl(baseUrl, position + 1));
            }
        }
    }

    protected String calculateBaseSearchUrl() {
        assert pk != null; //Ha senso solo in modalita' read/detail
        String baseUrl = dispatch.getAbsoluteOriginalPath();
        for(int i = 0; i < pk.length; i++) {
            int lastSlashIndex = baseUrl.lastIndexOf('/');
            baseUrl = baseUrl.substring(0, lastSlashIndex);
        }
        return baseUrl;
    }

    protected String generateObjectUrl(String baseUrl, int index) {
        Object o = objects.get(index);
        return generateObjectUrl(baseUrl, o);
    }

    protected String generateObjectUrl(String baseUrl, Object o) {
        String[] objPk = pkHelper.generatePkStringArray(o);
        String url = baseUrl + "/" + getPkForUrl(objPk);
        return new UrlBuilder(
                Locale.getDefault(), appendSearchStringParamIfNecessary(url), false)
                .toString();
    }

    protected void setupSearchForm() {
        SearchFormBuilder searchFormBuilder =
                new SearchFormBuilder(classAccessor);

        // setup option providers
        for (CrudSelectionProvider current : crudSelectionProviders) {
            SelectionProvider selectionProvider =
                    current.getSelectionProvider();
            if(selectionProvider == null) {
                continue;
            }
            String[] fieldNames = current.getFieldNames();
            searchFormBuilder.configSelectionProvider(selectionProvider, fieldNames);
        }

        searchForm = searchFormBuilder
                .configPrefix(searchPrefix)
                .build();

        if(!isEmbedded()) {
            logger.debug("Search form not embedded, no risk of clashes - reading parameters from request");
            readSearchFormFromRequest();
        }
    }

    protected void readSearchFormFromRequest() {
        if (StringUtils.isBlank(searchString)) {
            searchForm.readFromRequest(context.getRequest());
            searchString = searchForm.toSearchString();
            if (searchString.length() == 0) {
                searchString = null;
            } else {
                searchVisible = true;
            }
        } else {
            DummyHttpServletRequest dummyRequest =
                    new DummyHttpServletRequest();
            String[] parts = searchString.split(",");
            Pattern pattern = Pattern.compile("(.*)=(.*)");
            for (String part : parts) {
                Matcher matcher = pattern.matcher(part);
                if (matcher.matches()) {
                    String key = matcher.group(1);
                    String value = matcher.group(2);
                    logger.debug("Matched part: {}={}", key, value);
                    dummyRequest.addParameter(key, value);
                } else {
                    logger.debug("Could not match part: {}", part);
                }
            }
            searchForm.readFromRequest(dummyRequest);
            searchVisible = true;
        }
    }

    protected void setupForm(Mode mode) {
        FormBuilder formBuilder = new FormBuilder(classAccessor);

        // setup option providers
        for (CrudSelectionProvider current : crudSelectionProviders) {
            SelectionProvider selectionProvider =
                    current.getSelectionProvider();
            if(selectionProvider == null) {
                continue;
            }
            String[] fieldNames = current.getFieldNames();
            if(object != null) {
                Object[] values = new Object[fieldNames.length];
                boolean valuesRead = true;
                for(int i = 0; i < fieldNames.length; i++) {
                    String fieldName = fieldNames[i];
                    try {
                        PropertyAccessor propertyAccessor = classAccessor.getProperty(fieldName);
                        values[i] = propertyAccessor.get(object);
                    } catch (Exception e) {
                        logger.error("Couldn't read property " + fieldName, e);
                        valuesRead = false;
                    }
                }
                if(valuesRead) {
                    selectionProvider.ensureActive(values);
                }
            }
            formBuilder.configSelectionProvider(selectionProvider, fieldNames);
        }

        form = formBuilder
                .configPrefix(prefix)
                .configMode(mode)
                .build();
    }

    protected void setupTableForm(Mode mode) {
        String readLinkExpression = getReadLinkExpression();
        OgnlTextFormat hrefFormat =
                OgnlTextFormat.create(readLinkExpression);
        hrefFormat.setUrl(true);
        String encoding = application.getPortofinoProperties().getString(PortofinoProperties.URL_ENCODING);
        hrefFormat.setEncoding(encoding);

        TableFormBuilder tableFormBuilder = new TableFormBuilder(classAccessor);

        // setup option providers
        for (CrudSelectionProvider current : crudSelectionProviders) {
            SelectionProvider selectionProvider =
                    current.getSelectionProvider();
            if(selectionProvider == null) {
                continue;
            }
            String[] fieldNames = current.getFieldNames();
            tableFormBuilder.configSelectionProvider(
                    selectionProvider, fieldNames);
        }

        for (PropertyAccessor property : classAccessor.getKeyProperties()) {
            tableFormBuilder.configHrefTextFormat(
                    property.getName(), hrefFormat);
        }

        int nRows;
        if (objects == null) {
            nRows = 0;
        } else {
            nRows = objects.size();
        }

        tableFormBuilder
                .configPrefix(prefix)
                .configNRows(nRows)
                .configMode(mode)
                .configReflectiveFields();

        boolean isShowingKey = false;
        for (PropertyAccessor property : classAccessor.getKeyProperties()) {
            if(tableFormBuilder.isPropertyVisible(property)) {
                isShowingKey = true;
                break;
            }
        }

        if(!isShowingKey) {
            for (PropertyAccessor property : classAccessor.getProperties()) {
                if(tableFormBuilder.isPropertyVisible(property)) {
                    tableFormBuilder.configHrefTextFormat(
                        property.getName(), hrefFormat);
                    break;
                }
            }
        }

        tableForm = tableFormBuilder.build();

        tableForm.setKeyGenerator(pkHelper.createPkGenerator());
        tableForm.setSelectable(true);
        if (objects != null) {
            tableForm.readFromObject(objects);
            refreshTableBlobDownloadHref();
        }
    }

    protected String getReadLinkExpression() {
        StringBuilder sb = new StringBuilder();
        sb.append(dispatch.getOriginalPath());
        sb.append("/");
        boolean first = true;

        for (PropertyAccessor property : classAccessor.getKeyProperties()) {
            if (first) {
                first = false;
            } else {
                sb.append("/");
            }
            sb.append("%{");
            sb.append(property.getName());
            sb.append("}");
        }
        appendSearchStringParamIfNecessary(sb);
        return sb.toString();
    }

    protected String appendSearchStringParamIfNecessary(String s) {
        return appendSearchStringParamIfNecessary(new StringBuilder(s)).toString();
    }

    protected StringBuilder appendSearchStringParamIfNecessary(StringBuilder sb) {
        String searchStringParam = getEncodedSearchStringParam();
        if(searchStringParam != null) {
            if(sb.indexOf("?") == -1) {
                sb.append('?');
            } else {
                sb.append('&');
            }
            sb.append(searchStringParam);
        }
        return sb;
    }

    protected String getEncodedSearchStringParam() {
        if(StringUtils.isBlank(searchString)) {
            return null;
        }
        String encodedSearchString = "searchString=";
        try {
            String encoding = application.getPortofinoProperties().getString(PortofinoProperties.URL_ENCODING);
            String encoded = URLEncoder.encode(searchString, encoding);
            if(searchString.equals(URLDecoder.decode(encoded, encoding))) {
                encodedSearchString += encoded;
            } else {
                logger.warn("Could not encode search string \"" + StringEscapeUtils.escapeJava(searchString) +
                            "\" with encoding " + encoding);
                return null;
            }
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
        return encodedSearchString;
    }

    //**************************************************************************
    // PortofinoAction implementation
    //**************************************************************************

    public Resolution prepare(PageInstance pageInstance, ActionBeanContext context) {
        this.pageInstance = pageInstance;
        this.crudConfiguration = (CrudConfiguration) pageInstance.getConfiguration();

        if (crudConfiguration == null) {
            logger.warn("Crud is not configured: " + pageInstance.getPath());
            return null;
        }

        Database actualDatabase = crudConfiguration.getActualDatabase();
        if (actualDatabase == null) {
            logger.warn("Crud " + crudConfiguration.getName() + " (" + pageInstance.getPath() + ") " +
                        "has an invalid database: " + crudConfiguration.getDatabase());
            return null;
        }

        baseTable = crudConfiguration.getActualTable();
        if (baseTable == null) {
            return null;
        }
        TableAccessor tableAccessor = new TableAccessor(baseTable);

        classAccessor = new CrudAccessor(crudConfiguration, tableAccessor);

        application = pageInstance.getApplication();
        pkHelper = new PkHelper(classAccessor);
        session = application.getSession(crudConfiguration.getDatabase());
        List<String> parameters = pageInstance.getParameters();
        if(!parameters.isEmpty()) {
            String encoding = application.getPortofinoProperties().getString(PortofinoProperties.URL_ENCODING);
            pk = parameters.toArray(new String[parameters.size()]);
            try {
                for(int i = 0; i < pk.length; i++) {
                    pk[i] = URLDecoder.decode(pk[i], encoding);
                }
            } catch (UnsupportedEncodingException e) {
                throw new Error(e);
            }
            OgnlContext ognlContext = ElementsThreadLocals.getOgnlContext();

            Serializable pkObject;
            try {
                pkObject = pkHelper.getPrimaryKey(pk);
            } catch (Exception e) {
                logger.warn("Invalid primary key", e);
                return notInUseCase(context, parameters);
            }
            object = QueryUtils.getObjectByPk(
                    application,
                    baseTable, pkObject,
                    crudConfiguration.getQuery(), null);
            if(object != null) {
                ognlContext.put(crudConfiguration.getActualVariable(), object);
                String description = ShortNameUtils.getName(classAccessor, object);
                pageInstance.setDescription(description);
            } else {
                return notInUseCase(context, parameters);
            }
        }
        return null;
    }

    protected Resolution notInUseCase(ActionBeanContext context, List<String> parameters) {
        logger.info("Not in use case: " + crudConfiguration.getName());
        Locale locale = context.getLocale();
        ResourceBundle resourceBundle = application.getBundle(locale);
        //Non getMessage poiche' this.context == null durante il prepare
        String msg = MessageFormat.format(resourceBundle.getString("crud.notInUseCase"),
                                          StringUtils.join(parameters, "/"));
        SessionMessages.addWarningMessage(msg);
        return new ForwardResolution("/layouts/redirect-to-last-working-page.jsp");
    }

    //**************************************************************************
    // Object loading
    //**************************************************************************

    public void loadObjects() {
        //Se si passano dati sbagliati al criterio restituisco messaggio d'errore
        // ma nessun risultato
        try {
            TableCriteria criteria = new TableCriteria(baseTable);
            if(searchForm != null) {
                searchForm.configureCriteria(criteria);
            }
            String databaseName = crudConfiguration.getActualTable().getDatabaseName();
            Session session = application.getSession(databaseName);
            if(!StringUtils.isBlank(sortProperty) && !StringUtils.isBlank(sortDirection)) {
                try {
                    PropertyAccessor orderByProperty = classAccessor.getProperty(sortProperty);
                    criteria.orderBy(orderByProperty, sortDirection);
                } catch (NoSuchFieldException e) {
                    logger.error("Can't order by " + sortProperty + ", property accessor not found", e);
                }
            }
            objects = QueryUtils.getObjects(session,
                    crudConfiguration.getQuery(), criteria, this, firstResult, maxResults);
        } catch (ClassCastException e) {
            objects=new ArrayList<Object>();
            logger.warn("Incorrect Field Type", e);
            SessionMessages.addWarningMessage(getMessage("crud.incorrectFieldType"));
        }
    }

    private void loadObject(String... pk) {
        Serializable pkObject = pkHelper.getPrimaryKey(pk);
        object = QueryUtils.getObjectByPk(
                application,
                baseTable, pkObject,
                crudConfiguration.getQuery(), null);
    }

    //**************************************************************************
    // ExportSearch
    //**************************************************************************

    @Button(list = "crud-search", key = "commons.exportExcel", order = 5)
    public Resolution exportSearchExcel() {
        try {
            final File tmpFile = File.createTempFile(crudConfiguration.getName() + ".search", ".xls");
            exportSearchExcel(tmpFile);
            FileInputStream fileInputStream = new FileInputStream(tmpFile);
            return new StreamingResolution("application/vnd.ms-excel", fileInputStream) {
                @Override
                protected void stream(HttpServletResponse response) throws Exception {
                    super.stream(response);
                    tmpFile.delete();
                }
            }.setFilename(crudConfiguration.getSearchTitle() + ".xls");
        } catch (Exception e) {
            logger.error("Excel export failed", e);
            SessionMessages.addErrorMessage(getMessage("commons.export.failed"));
            return new RedirectResolution(dispatch.getOriginalPath());
        }
    }

    public void exportSearchExcel(File fileTemp) {
        setupSearchForm();
        loadObjects();
        setupTableForm(Mode.VIEW);

        writeFileSearchExcel(fileTemp);
    }

    private void writeFileSearchExcel(File fileTemp) {
        WritableWorkbook workbook = null;
        try {
            workbook = Workbook.createWorkbook(fileTemp);
            String title = crudConfiguration.getSearchTitle();
            if(StringUtils.isBlank(title)) {
                title = "export";
            }
            WritableSheet sheet =
                    workbook.createSheet(title, 0);

            addHeaderToSearchSheet(sheet);

            int i = 1;
            for ( TableForm.Row row : tableForm.getRows()) {
                exportRows(sheet, i, row);
                i++;
            }

            workbook.write();
        } catch (IOException e) {
            logger.warn("IOException", e);
            SessionMessages.addErrorMessage(e.getMessage());
        } catch (RowsExceededException e) {
            logger.warn("RowsExceededException", e);
            SessionMessages.addErrorMessage(e.getMessage());
        } catch (WriteException e) {
            logger.warn("WriteException", e);
            SessionMessages.addErrorMessage(e.getMessage());
        } finally {
            try {
                if (workbook != null)
                    workbook.close();
            }
            catch (Exception e) {
                logger.warn("IOException", e);
                SessionMessages.addErrorMessage(e.getMessage());
            }
        }
    }

    //**************************************************************************
    // ExportRead
    //**************************************************************************

    @Button(list = "crud-read", key = "commons.exportExcel", order = 4)
    public Resolution exportReadExcel() {
        try {
            final File tmpFile = File.createTempFile(crudConfiguration.getName(), ".read.xls");
            exportReadExcel(tmpFile);
            FileInputStream fileInputStream = new FileInputStream(tmpFile);
            return new StreamingResolution("application/vnd.ms-excel", fileInputStream) {
                @Override
                protected void stream(HttpServletResponse response) throws Exception {
                    super.stream(response);
                    tmpFile.delete();
                }
            }.setFilename(crudConfiguration.getReadTitle() + ".xls");
        } catch (Exception e) {
            logger.error("Excel export failed", e);
            SessionMessages.addErrorMessage(getMessage("commons.export.failed"));
            return new RedirectResolution(dispatch.getOriginalPath());
        }
    }

    public void exportReadExcel(File tempFile)
            throws IOException, WriteException {
        setupSearchForm();

        loadObjects();

        setupForm(Mode.VIEW);
        form.readFromObject(object);

        writeFileReadExcel(tempFile);
    }

    private void writeFileReadExcel(File fileTemp)
            throws IOException, WriteException {
        WritableWorkbook workbook = null;
        try {
            workbook = Workbook.createWorkbook(fileTemp);
            WritableSheet sheet =
                workbook.createSheet(crudConfiguration.getReadTitle(),
                        workbook.getNumberOfSheets());

            addHeaderToReadSheet(sheet);

            int i = 1;
            for (FieldSet fieldset : form) {
                int j = 0;
                for (Field field : fieldset) {
                    addFieldToCell(sheet, i, j, field);
                    j++;
                }
                i++;
            }

            /*
            ValueStack valueStack = Struts2Utils.getValueStack();
            valueStack.push(object);

            //Aggiungo le relazioni/sheet
            WritableCellFormat formatCell = headerExcel();
            for (CrudUnit subCrudUnit: subCrudUnits) {
                subCrudUnit.setupSearchForm();
                subCrudUnit.loadObjects();
                subCrudUnit.setupTableForm(Mode.VIEW);

                sheet = workbook.createSheet(subCrudUnit.searchTitle ,
                        workbook.getNumberOfSheets());

                int m = 0;
                for (TableForm.Column col : subCrudUnit.tableForm.getColumns()) {
                    sheet.addCell(new Label(m, 0, col.getLabel(), formatCell));
                    m++;
                }
                int k = 1;
                for (TableForm.Row row : subCrudUnit.tableForm.getRows()) {
                    int j = 0;
                    for (Field field : Arrays.asList(row.getFields())) {
                        addFieldToCell(sheet, k, j, field);
                        j++;
                    }
                    k++;
                }
            }
            valueStack.pop();
            */
            workbook.write();
        } catch (IOException e) {
            logger.warn("IOException", e);
            SessionMessages.addErrorMessage(e.getMessage());
        } catch (RowsExceededException e) {
            logger.warn("RowsExceededException", e);
            SessionMessages.addErrorMessage(e.getMessage());
        } catch (WriteException e) {
            logger.warn("WriteException", e);
            SessionMessages.addErrorMessage(e.getMessage());
        } finally {
            try {
                if (workbook != null)
                    workbook.close();
            }
            catch (Exception e) {
                logger.warn("IOException", e);
                SessionMessages.addErrorMessage(e.getMessage());
            }
        }
    }


    private WritableCellFormat headerExcel() {
        WritableFont fontCell = new WritableFont(WritableFont.ARIAL, 12,
             WritableFont.BOLD, true);
        return new WritableCellFormat (fontCell);
    }

    private void exportRows(WritableSheet sheet, int i,
                            TableForm.Row row) throws WriteException {
        int j = 0;
        for (Field field : row) {
            addFieldToCell(sheet, i, j, field);
            j++;
        }
    }


    private void addHeaderToReadSheet(WritableSheet sheet) throws WriteException {
        WritableCellFormat formatCell = headerExcel();
        int i = 0;
        for (FieldSet fieldset : form) {
            for (Field field : fieldset) {
                sheet.addCell(new jxl.write.Label(i, 0, field.getLabel(), formatCell));
                i++;
            }
        }
    }

    private void addHeaderToSearchSheet(WritableSheet sheet) throws WriteException {
        WritableCellFormat formatCell = headerExcel();
        int l = 0;
        for (TableForm.Column col : tableForm.getColumns()) {
            sheet.addCell(new jxl.write.Label(l, 0, col.getLabel(), formatCell));
            l++;
        }
    }

    private void addFieldToCell(WritableSheet sheet, int i, int j,
                                Field field) throws WriteException {
        if (field instanceof NumericField) {
            NumericField numField = (NumericField) field;
            if (numField.getValue() != null) {
                Number number;
                BigDecimal decimalValue = numField.getValue();
                if (numField.getDecimalFormat() == null) {
                    number = new Number(j, i,
                            decimalValue == null
                                    ? null : decimalValue.doubleValue());
                } else {
                    NumberFormat numberFormat = new NumberFormat(
                            numField.getDecimalFormat().toPattern());
                    WritableCellFormat writeCellNumberFormat =
                            new WritableCellFormat(numberFormat);
                    number = new Number(j, i,
                            decimalValue == null
                                    ? null : decimalValue.doubleValue(),
                            writeCellNumberFormat);
                }
                sheet.addCell(number);
            }
        } else if (field instanceof PasswordField) {
            jxl.write.Label label = new jxl.write.Label(j, i,
                    PasswordField.PASSWORD_PLACEHOLDER);
            sheet.addCell(label);
        } else if (field instanceof DateField) {
            DateField dateField = (DateField) field;
            DateTime dateCell;
            Date date = dateField.getValue();
            if (date != null) {
                DateFormat dateFormat = new DateFormat(
                        dateField.getDatePattern());
                WritableCellFormat wDateFormat =
                        new WritableCellFormat(dateFormat);
                dateCell = new DateTime(j, i,
                        dateField.getValue() == null
                                ? null : dateField.getValue(),
                        wDateFormat);
                sheet.addCell(dateCell);
            }
        } else {
            jxl.write.Label label = new jxl.write.Label(j, i, field.getStringValue());
            sheet.addCell(label);
        }
    }



    //**************************************************************************
    // exportSearchPdf
    //**************************************************************************

    @Button(list = "crud-search", key = "commons.exportPdf", order = 4)
    public Resolution exportSearchPdf() {
        try {
            final File tmpFile = File.createTempFile(crudConfiguration.getName() + ".search", ".pdf");
            exportSearchPdf(tmpFile);
            FileInputStream fileInputStream = new FileInputStream(tmpFile);
            return new StreamingResolution(MimeTypes.APPLICATION_PDF, fileInputStream) {
                @Override
                protected void stream(HttpServletResponse response) throws Exception {
                    super.stream(response);
                    tmpFile.delete();
                }
            }.setFilename(crudConfiguration.getSearchTitle() + ".pdf");
        } catch (Exception e) {
            logger.error("PDF export failed", e);
            SessionMessages.addErrorMessage(getMessage("commons.export.failed"));
            return new RedirectResolution(dispatch.getOriginalPath());
        }
    }

    public void exportSearchPdf(File tempPdfFile) throws FOPException,
            IOException, TransformerException {

        setupSearchForm();

        loadObjects();

        setupTableForm(Mode.VIEW);

        FopFactory fopFactory = FopFactory.newInstance();

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tempPdfFile);

            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, out);

            InputStream xsltStream = getSearchPdfXsltStream();

            // Setup XSLT
            TransformerFactory Factory = TransformerFactory.newInstance();
            Transformer transformer = Factory.newTransformer(new StreamSource(
                    xsltStream));

            // Set the value of a <param> in the stylesheet
            transformer.setParameter("versionParam", "2.0");

            // Setup input for XSLT transformation
            Source src = new StreamSource(new StringReader(
                    composeXmlSearch().toString()));

            // Resulting SAX events (the generated FO) must be piped through to
            // FOP
            Result res = new SAXResult(fop.getDefaultHandler());

            // Start XSLT transformation and FOP processing
            transformer.transform(src, res);

            out.flush();
        } catch (Exception e) {
            logger.warn("IOException", e);
            SessionMessages.addErrorMessage(e.getMessage());
        } finally {
            try {
                if (out != null)
                    out.close();
            }
            catch (Exception e) {
                logger.warn("IOException", e);
                SessionMessages.addErrorMessage(e.getMessage());
            }
        }
    }

    protected InputStream getSearchPdfXsltStream() {
        ClassLoader cl = getClass().getClassLoader();
        return cl.getResourceAsStream(
                TEMPLATE_FOP_SEARCH);
    }

    public XmlBuffer composeXmlSearch() {
        XmlBuffer xb = new XmlBuffer();
        xb.writeXmlHeader("UTF-8");
        xb.openElement("class");
        xb.openElement("table");
        xb.write(crudConfiguration.getSearchTitle());
        xb.closeElement("table");

        for (TableForm.Column col : tableForm.getColumns()) {
            xb.openElement("header");
            xb.openElement("nameColumn");
            xb.write(col.getLabel());
            xb.closeElement("nameColumn");
            xb.closeElement("header");
        }


        for (TableForm.Row row : tableForm.getRows()) {
            xb.openElement("rows");
            for (Field field : row) {
                xb.openElement("row");
                xb.openElement("value");
                xb.write(field.getStringValue());
                xb.closeElement("value");
                xb.closeElement("row");
            }
            xb.closeElement("rows");
        }

        xb.closeElement("class");

        return xb;
    }


    //**************************************************************************
    // ExportRead
    //**************************************************************************

    private XmlBuffer composeXmlPort()
            throws IOException, WriteException {
        setupSearchForm();

        loadObjects();

        setupTableForm(Mode.VIEW);
        setupForm(Mode.VIEW);
        form.readFromObject(object);


        XmlBuffer xb = new XmlBuffer();
        xb.writeXmlHeader("UTF-8");
        xb.openElement("class");
        xb.openElement("table");
        xb.write(crudConfiguration.getReadTitle());
        xb.closeElement("table");

        for (FieldSet fieldset : form) {
            xb.openElement("tableData");
            xb.openElement("rows");

            for (Field field : fieldset) {
                xb.openElement("row");
                xb.openElement("nameColumn");
                xb.write(field.getLabel());
                xb.closeElement("nameColumn");

                xb.openElement("value");
                xb.write(field.getStringValue());
                xb.closeElement("value");
                xb.closeElement("row");

            }
            xb.closeElement("rows");
            xb.closeElement("tableData");
        }

        xb.closeElement("class");
        return xb;
    }

     public void exportReadPdf(File tempPdfFile) throws FOPException,
            IOException, TransformerException {
        setupSearchForm();

        loadObjects();

        setupTableForm(Mode.VIEW);

        FopFactory fopFactory = FopFactory.newInstance();

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tempPdfFile);

            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, out);

            ClassLoader cl = getClass().getClassLoader();
            InputStream xsltStream = cl.getResourceAsStream(
                    TEMPLATE_FOP_READ);

            // Setup XSLT
            TransformerFactory Factory = TransformerFactory.newInstance();
            Transformer transformer = Factory.newTransformer(new StreamSource(
                    xsltStream));

            // Set the value of a <param> in the stylesheet
            transformer.setParameter("versionParam", "2.0");

            // Setup input for XSLT transformation
            Source src = new StreamSource(new StringReader(
                    composeXmlPort().toString()));

            // Resulting SAX events (the generated FO) must be piped through to
            // FOP
            Result res = new SAXResult(fop.getDefaultHandler());

            // Start XSLT transformation and FOP processing
            transformer.transform(src, res);

            out.flush();
        } catch (Exception e) {
            logger.warn("IOException", e);
            SessionMessages.addErrorMessage(e.getMessage());
        } finally {
            try {
                if (out != null)
                    out.close();
            }
            catch (Exception e) {
                logger.warn("IOException", e);
                SessionMessages.addErrorMessage(e.getMessage());
            }
        }
    }

    @Button(list = "crud-read", key = "commons.exportPdf", order = 3)
    public Resolution exportReadPdf() {
        try {
            final File tmpFile = File.createTempFile("export." + crudConfiguration.getName(), ".read.pdf");
            exportReadPdf(tmpFile);
            FileInputStream fileInputStream = new FileInputStream(tmpFile);
            return new StreamingResolution("application/pdf", fileInputStream) {
                @Override
                protected void stream(HttpServletResponse response) throws Exception {
                    super.stream(response);
                    tmpFile.delete();
                }
            }.setFilename(crudConfiguration.getReadTitle() + ".pdf");
        } catch (Exception e) {
            logger.error("PDF export failed", e);
            SessionMessages.addErrorMessage(getMessage("commons.export.failed"));
            return new RedirectResolution(dispatch.getOriginalPath());
        }
    }

    //**************************************************************************
    // Hooks/scripting
    //**************************************************************************

    protected void createSetup(Object object) {}

    protected boolean createValidate(Object object) {
        return true;
    }

    protected void createPostProcess(Object object) {}


    protected void editSetup(Object object) {}

    protected boolean editValidate(Object object) {
        return true;
    }

    protected void editPostProcess(Object object) {}


    protected boolean deleteValidate(Object object) {
        return true;
    }

    protected void deletePostProcess(Object object) {}
    

    protected Resolution getBulkEditView() {
        return new ForwardResolution("/layouts/crud/bulk-edit.jsp");
    }

    protected Resolution getCreateView() {
        return new ForwardResolution("/layouts/crud/create.jsp");
    }

    protected Resolution getEditView() {
        return new ForwardResolution("/layouts/crud/edit.jsp");
    }

    protected Resolution getReadView() {
        return forwardToPortletPage("/layouts/crud/read.jsp");
    }

    protected Resolution getSearchView() {
        return forwardToPortletPage("/layouts/crud/search.jsp");
    }

    protected Resolution getEmbeddedSearchView() {
        return new ForwardResolution("/layouts/crud/search.jsp");
        //return new ForwardResolution("/layouts/crud/embedded-search.jsp");
    }

    //**************************************************************************
    // Configuration
    //**************************************************************************

    public static final String[][] CRUD_CONFIGURATION_FIELDS =
            {{"name", "database", "query", "searchTitle", "createTitle", "readTitle", "editTitle", "variable",
              "largeResultSet", "rowsPerPage"}};

    public Form crudConfigurationForm;
    public TableForm propertiesTableForm;
    public CrudPropertyEdit[] edits;
    public TableForm selectionProvidersForm;
    public CrudSelectionProviderEdit[] selectionProviderEdits;

    @Button(list = "portletHeaderButtons", key = "commons.configure", order = 1, icon = "ui-icon-wrench")
    @RequiresPermissions(level = AccessLevel.DEVELOP)
    public Resolution configure() {
        prepareConfigurationForms();

        crudConfigurationForm.readFromObject(crudConfiguration);
        if(edits != null) {
            propertiesTableForm.readFromObject(edits);
        }

        if(selectionProviderEdits != null) {
            selectionProvidersForm.readFromObject(selectionProviderEdits);
        }

        return new ForwardResolution("/layouts/crud/configure.jsp");
    }

    @Override
    protected void prepareConfigurationForms() {
        super.prepareConfigurationForms();

        SelectionProvider databaseSelectionProvider =
                SelectionProviderLogic.createSelectionProvider(
                        "database",
                        model.getDatabases(),
                        Database.class,
                        null,
                        new String[]{"databaseName"});
        crudConfigurationForm = new FormBuilder(CrudConfiguration.class)
                .configFields(CRUD_CONFIGURATION_FIELDS)
                .configFieldSetNames("Crud")
                .configSelectionProvider(databaseSelectionProvider, "database")
                .build();

        setupEdits();

        if(edits != null) {
            TableFormBuilder tableFormBuilder =
                    new TableFormBuilder(CrudPropertyEdit.class)
                        .configNRows(edits.length);
            propertiesTableForm = tableFormBuilder.build();
        }

        if(!availableSelectionProviders.isEmpty()) {
            setupSelectionProviderEdits();
            TableFormBuilder tableFormBuilder =
                    new TableFormBuilder(CrudSelectionProviderEdit.class);
            tableFormBuilder.configNRows(availableSelectionProviders.size());
            for(int i = 0; i < selectionProviderEdits.length; i++) {
                Collection<ModelSelectionProvider> availableProviders =
                        (Collection) availableSelectionProviders.get
                                (Arrays.asList(selectionProviderEdits[i].fieldNames));
                if(availableProviders == null || availableProviders.size() == 0) {
                    continue;
                }
                DefaultSelectionProvider selectionProvider =
                        new DefaultSelectionProvider(selectionProviderEdits[i].columns);
                selectionProvider.appendRow(null, "None", true);
                for(ModelSelectionProvider sp : availableProviders) {
                    selectionProvider.appendRow(sp.getName(), sp.getName(), true);
                }
                tableFormBuilder.configSelectionProvider(i, selectionProvider, "selectionProvider");
            }
            selectionProvidersForm = tableFormBuilder.build();
        }
    }

    private void setupEdits() {
        if(classAccessor == null) {
            return;
        }
        PropertyAccessor[] propertyAccessors = classAccessor.getProperties();
        edits = new CrudPropertyEdit[propertyAccessors.length];
        for (int i = 0; i < propertyAccessors.length; i++) {
            CrudPropertyEdit edit = new CrudPropertyEdit();
            PropertyAccessor propertyAccessor = propertyAccessors[i];
            edit.name = propertyAccessor.getName();
            Label labelAnn = propertyAccessor.getAnnotation(Label.class);
            edit.label = labelAnn != null ? labelAnn.value() : null;
            Enabled enabledAnn = propertyAccessor.getAnnotation(Enabled.class);
            edit.enabled = enabledAnn != null && enabledAnn.value();
            InSummary inSummaryAnn = propertyAccessor.getAnnotation(InSummary.class);
            edit.inSummary = inSummaryAnn != null && inSummaryAnn.value();
            Insertable insertableAnn = propertyAccessor.getAnnotation(Insertable.class);
            edit.insertable = insertableAnn != null && insertableAnn.value();
            Updatable updatableAnn = propertyAccessor.getAnnotation(Updatable.class);
            edit.updatable = updatableAnn != null && updatableAnn.value();
            Searchable searchableAnn = propertyAccessor.getAnnotation(Searchable.class);
            edit.searchable = searchableAnn != null && searchableAnn.value();
            edits[i] = edit;
        }
    }

    private void setupSelectionProviderEdits() {
        selectionProviderEdits = new CrudSelectionProviderEdit[availableSelectionProviders.size()];
        int i = 0;
        for(Map.Entry entry : (Set<Map.Entry>) availableSelectionProviders.entrySet()) {
            selectionProviderEdits[i] = new CrudSelectionProviderEdit();
            String[] fieldNames = (String[]) ((Collection) entry.getKey()).toArray(new String[0]);
            selectionProviderEdits[i].fieldNames = fieldNames;
            selectionProviderEdits[i].columns = StringUtils.join(fieldNames, ", ");
            for(CrudSelectionProvider cp : crudSelectionProviders) {
                if(Arrays.equals(cp.fieldNames, fieldNames)) {
                    SelectionProvider selectionProvider = cp.getSelectionProvider();
                    if(selectionProvider != null) {
                        selectionProviderEdits[i].selectionProvider = selectionProvider.getName();
                        selectionProviderEdits[i].displayMode = selectionProvider.getDisplayMode();
                    } else {
                        selectionProviderEdits[i].selectionProvider = null;
                        selectionProviderEdits[i].displayMode = DisplayMode.DROPDOWN;
                    }
                }
            }
            i++;
        }
    }

    @Button(list = "configuration", key = "commons.updateConfiguration")
    @RequiresPermissions(level = AccessLevel.DEVELOP)
    public Resolution updateConfiguration() {
        prepareConfigurationForms();

        crudConfigurationForm.readFromObject(crudConfiguration);

        readPageConfigurationFromRequest();

        crudConfigurationForm.readFromRequest(context.getRequest());

        boolean valid = crudConfigurationForm.validate();
        valid = validatePageConfiguration() && valid;

        if(propertiesTableForm != null) {
            propertiesTableForm.readFromObject(edits);
            propertiesTableForm.readFromRequest(context.getRequest());
            valid = propertiesTableForm.validate() && valid;
        }

        if(selectionProvidersForm != null) {
            selectionProvidersForm.readFromRequest(context.getRequest());
            valid = selectionProvidersForm.validate() && valid;
        }

        if (valid) {
            updatePageConfiguration();
            if(crudConfiguration == null) {
                crudConfiguration = new CrudConfiguration();
            }
            crudConfigurationForm.writeToObject(crudConfiguration);

            if(propertiesTableForm != null) {
                updateProperties();
            }

            if(!availableSelectionProviders.isEmpty()) {
                updateSelectionProviders();
            }

            saveConfiguration(crudConfiguration);

            SessionMessages.addInfoMessage(getMessage("commons.configuration.updated"));
            return cancel();
        } else {
            SessionMessages.addErrorMessage(getMessage("commons.configuration.notUpdated"));
            return new ForwardResolution("/layouts/crud/configure.jsp");
        }
    }

    private void updateSelectionProviders() {
        selectionProvidersForm.writeToObject(selectionProviderEdits);
        crudConfiguration.getSelectionProviders().clear();
        for(CrudSelectionProviderEdit sp : selectionProviderEdits) {
            if(sp.selectionProvider == null) {
                //TODO this is a shortcut: takes the first available selection provider and disables it
                List<String> key = Arrays.asList(sp.fieldNames);
                Collection<ModelSelectionProvider> selectionProviders =
                        (Collection<ModelSelectionProvider>) availableSelectionProviders.get(key);
                ModelSelectionProvider dsp = selectionProviders.iterator().next();
                SelectionProviderReference sel = makeSelectionProviderReference(dsp);
                sel.setEnabled(false);
            } else {
                List<String> key = Arrays.asList(sp.fieldNames);
                Collection<ModelSelectionProvider> selectionProviders =
                        (Collection<ModelSelectionProvider>) availableSelectionProviders.get(key);
                for(ModelSelectionProvider dsp : selectionProviders) {
                    if(sp.selectionProvider.equals(dsp.getName())) {
                        SelectionProviderReference sel = makeSelectionProviderReference(dsp);
                        sel.setDisplayMode(sp.displayMode);
                        break;
                    }
                }
            }
        }
    }

    private void updateProperties() {
        propertiesTableForm.writeToObject(edits);

        List<CrudProperty> newProperties = new ArrayList<CrudProperty>();
        for (CrudPropertyEdit edit : edits) {
            CrudProperty crudProperty = findProperty(edit.name, crudConfiguration.getProperties());
            if(crudProperty == null) {
                crudProperty = new CrudProperty();
            }

            crudProperty.setName(edit.name);
            crudProperty.setLabel(edit.label);
            crudProperty.setInSummary(edit.inSummary);
            crudProperty.setSearchable(edit.searchable);
            crudProperty.setEnabled(edit.enabled);
            crudProperty.setInsertable(edit.insertable);
            crudProperty.setUpdatable(edit.updatable);

            newProperties.add(crudProperty);
        }
        crudConfiguration.getProperties().clear();
        crudConfiguration.getProperties().addAll(newProperties);
    }

    protected CrudProperty findProperty(String name, List<CrudProperty> properties) {
        for(CrudProperty p : properties) {
            if(p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    protected SelectionProviderReference makeSelectionProviderReference(ModelSelectionProvider dsp) {
        SelectionProviderReference sel = new SelectionProviderReference();
        if(dsp instanceof ForeignKey) {
            sel.setForeignKeyName(dsp.getName());
        } else {
            sel.setSelectionProviderName(dsp.getName());
        }
        crudConfiguration.getSelectionProviders().add(sel);
        return sel;
    }

    public boolean isRequiredFieldsPresent() {
        return form.isRequiredFieldsPresent();
    }

    public CrudConfiguration getCrudConfiguration() {
        return crudConfiguration;
    }

    public void setCrudConfiguration(CrudConfiguration crudConfiguration) {
        this.crudConfiguration = crudConfiguration;
    }

    public ClassAccessor getClassAccessor() {
        return classAccessor;
    }

    public void setClassAccessor(ClassAccessor classAccessor) {
        this.classAccessor = classAccessor;
    }

    public Table getBaseTable() {
        return baseTable;
    }

    public void setBaseTable(Table baseTable) {
        this.baseTable = baseTable;
    }

    public PkHelper getPkHelper() {
        return pkHelper;
    }

    public void setPkHelper(PkHelper pkHelper) {
        this.pkHelper = pkHelper;
    }

    public List<CrudSelectionProvider> getCrudSelectionProviders() {
        return crudSelectionProviders;
    }

    public void setCrudSelectionProviders(List<CrudSelectionProvider> crudSelectionProviders) {
        this.crudSelectionProviders = crudSelectionProviders;
    }

    public String[] getSelection() {
        return selection;
    }

    public void setSelection(String[] selection) {
        this.selection = selection;
    }

    public String getSearchString() {
        return searchString;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }

    public String getSuccessReturnUrl() {
        return successReturnUrl;
    }

    public void setSuccessReturnUrl(String successReturnUrl) {
        this.successReturnUrl = successReturnUrl;
    }

    public SearchForm getSearchForm() {
        return searchForm;
    }

    public void setSearchForm(SearchForm searchForm) {
        this.searchForm = searchForm;
    }

    public List getObjects() {
        return objects;
    }

    public void setObjects(List objects) {
        this.objects = objects;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    public boolean isMultipartRequest() {
        return form != null && form.isMultipartRequest();
    }

    public Form getCrudConfigurationForm() {
        return crudConfigurationForm;
    }

    public void setCrudConfigurationForm(Form crudConfigurationForm) {
        this.crudConfigurationForm = crudConfigurationForm;
    }

    public TableForm getPropertiesTableForm() {
        return propertiesTableForm;
    }

    public Form getForm() {
        return form;
    }

    public void setForm(Form form) {
        this.form = form;
    }

    public TableForm getTableForm() {
        return tableForm;
    }

    public void setTableForm(TableForm tableForm) {
        this.tableForm = tableForm;
    }

    public TableForm getSelectionProvidersForm() {
        return selectionProvidersForm;
    }
    
    public Integer getFirstResult() {
        return firstResult;
    }

    public void setFirstResult(Integer firstResult) {
        this.firstResult = firstResult;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    public String getSortProperty() {
        return sortProperty;
    }

    public void setSortProperty(String sortProperty) {
        this.sortProperty = sortProperty;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public boolean isSearchVisible() {
        return searchVisible;
    }

    public void setSearchVisible(boolean searchVisible) {
        this.searchVisible = searchVisible;
    }

    public String getRelName() {
        return relName;
    }

    public void setRelName(String relName) {
        this.relName = relName;
    }

    public int getSelectionProviderIndex() {
        return selectionProviderIndex;
    }

    public void setSelectionProviderIndex(int selectionProviderIndex) {
        this.selectionProviderIndex = selectionProviderIndex;
    }

    public String getSelectFieldMode() {
        return selectFieldMode;
    }

    public void setSelectFieldMode(String selectFieldMode) {
        this.selectFieldMode = selectFieldMode;
    }

    public String getLabelSearch() {
        return labelSearch;
    }

    public void setLabelSearch(String labelSearch) {
        this.labelSearch = labelSearch;
    }
}
