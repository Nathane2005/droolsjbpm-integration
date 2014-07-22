package org.kie.remote.services.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.jbpm.process.audit.AuditLogService;
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.process.audit.VariableInstanceLog;
import org.jbpm.process.audit.event.AuditEvent;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.services.client.serialization.jaxb.impl.audit.JaxbHistoryLogList;
import org.kie.services.client.serialization.jaxb.impl.audit.JaxbProcessInstanceLog;
import org.kie.services.client.serialization.jaxb.rest.JaxbGenericResponse;
import org.kie.workbench.common.services.rest.RestOperationException;
import org.kie.remote.services.cdi.ProcessRequestBean;

/**
 * If a method in this class is annotated by a @Path annotation, 
 * then the name of the method should match the URL specified in the @Path, 
 * where "_" characters should be used for all "/" characters in the path. 
 * <p>
 * For example: 
 * <pre>
 * @Path("/begin/{varOne: [_a-zA-Z0-9-:\\.]+}/midddle/{varTwo: [a-z]+}")
 * public void begin_varOne_middle_varTwo() { 
 * </pre>
 * 
 * If the method is annotated by the @Path anno, but is the "root", then
 * give it a name that explains it's funtion.
 */
@Path("/history")
@RequestScoped
@SuppressWarnings("unchecked")
public class HistoryResource extends ResourceBase {

    /* REST information */
    @Context
    private HttpHeaders headers;
    
    @Context
    private UriInfo uriInfo;
    
    @Context
    private Request restRequest;

    @Inject
    private ProcessRequestBean processRequestBean;
    
    // Rest methods --------------------------------------------------------------------------------------------------------------

    private AuditLogService getAuditLogService() { 
        return processRequestBean.getAuditLogService();
    }
    
    /**
     * History methods
     */
    
    @POST
    @Path("/clear")
    public Response clear() {
        getAuditLogService().clear();
        return createCorrectVariant(new JaxbGenericResponse(uriInfo.getRequestUri().toString()), headers);
    }

    @GET
    @Path("/instances")
    public Response instances() {
        String oper = getRelativePath(uriInfo);
        Map<String, List<String>> params = getRequestParams(uriInfo);
        
        List<ProcessInstanceLog> procInstLogResults = getAuditLogService().findProcessInstances();
        
        List<AuditEvent> results = new ArrayList<AuditEvent>(procInstLogResults);
        JaxbHistoryLogList resultList =  paginateAndCreateResult(params, oper, results, new JaxbHistoryLogList());
        
        return createCorrectVariant(resultList, headers);
    }

    @GET
    @Path("/instance/{procInstId: [0-9]+}")
    // TODO: docs
    public Response instance_procInstId(@PathParam("procInstId") long procInstId) {
        ProcessInstanceLog procInstLog = getAuditLogService().findProcessInstance(procInstId);
        JaxbProcessInstanceLog jaxbProcLog = new JaxbProcessInstanceLog(procInstLog);
        
        return createCorrectVariant(jaxbProcLog, headers);
    }

    @GET
    @Path("/instance/{procInstId: [0-9]+}/{oper: [a-zA-Z]+}")
    public Response instance_procInstid_oper(@PathParam("procInstId") Long procInstId, @PathParam("oper") String operation) {
        Map<String, List<String>> params = getRequestParams(uriInfo);
        String oper = getRelativePath(uriInfo);
        
        Object result = null;
        if ("child".equalsIgnoreCase(operation)) {
            result = getAuditLogService().findSubProcessInstances(procInstId);
        } else if ("node".equalsIgnoreCase(operation)) {
            result = getAuditLogService().findNodeInstances(procInstId);
        } else if ("variable".equalsIgnoreCase(operation)) {
            result = getAuditLogService().findVariableInstances(procInstId);
        } else {
            throw RestOperationException.badRequest("Unsupported operation: " + oper );
        }

        List<AuditEvent> varInstLogList = (List<AuditEvent>) result;
        JaxbHistoryLogList resultList =  paginateAndCreateResult(params, oper, varInstLogList, new JaxbHistoryLogList());
        
        return createCorrectVariant(resultList, headers);
    }

    @GET
    @Path("/instance/{procInstId: [0-9]+}/{oper: [a-zA-Z]+}/{logId: [a-zA-Z0-9-:\\._]+}")
    public Response instance_procInstId_oper_logId(@PathParam("procInstId") Long procInstId,
            @PathParam("oper") String operation, @PathParam("logId") String logId) {
        Map<String, List<String>> params = getRequestParams(uriInfo);
        String oper = getRelativePath(uriInfo);
        
        Object result = null;
        if ("node".equalsIgnoreCase(operation)) {
            result = getAuditLogService().findNodeInstances(procInstId, logId);
        } else if ("variable".equalsIgnoreCase(operation)) {
            result = getAuditLogService().findVariableInstances(procInstId, logId);
        } else {
            throw RestOperationException.badRequest("Unsupported operation: " + oper );
        }
        
        List<AuditEvent> varInstLogList = (List<AuditEvent>) result;
        JaxbHistoryLogList resultList = paginateAndCreateResult(params, oper, varInstLogList, new JaxbHistoryLogList());
        
        return createCorrectVariant(resultList, headers);
    }

    @GET
    @Path("/process/{processDefId: [a-zA-Z0-9-:\\._]+}")
    public Response process_procDefId(@PathParam("processDefId") String processId) {
        Map<String, List<String>> params = getRequestParams(uriInfo);
        Number statusParam = getNumberParam("status", false, params, getRelativePath(uriInfo), false);
        String oper = getRelativePath(uriInfo);
        int[] pageInfo = getPageNumAndPageSize(params, oper);

        Object result;
        if (statusParam != null) {
            if (statusParam.intValue() == ProcessInstance.STATE_ACTIVE) {
                result = getAuditLogService().findActiveProcessInstances(processId);
            } else {
                result = getAuditLogService().findProcessInstances(processId);
            }
        } else {
            result = getAuditLogService().findProcessInstances(processId);
        }
        
        List<ProcessInstanceLog> procInstLogList = (List<ProcessInstanceLog>) result;
        
        List<ProcessInstanceLog> filteredProcLogList = procInstLogList;
        if (statusParam != null && !statusParam.equals(ProcessInstance.STATE_ACTIVE)) {
            filteredProcLogList = new ArrayList<ProcessInstanceLog>();
            for (int i = 0; 
                    i < procInstLogList.size() && filteredProcLogList.size() < getMaxNumResultsNeeded(pageInfo);
                    ++i) {
                ProcessInstanceLog procLog = procInstLogList.get(i);
                if (procLog.getStatus().equals(statusParam.intValue())) {
                    filteredProcLogList.add(procLog);
                }
            }
        }
        List<AuditEvent> results = new ArrayList<AuditEvent>(filteredProcLogList);
        
        JaxbHistoryLogList resultList = paginateAndCreateResult(pageInfo, results, new JaxbHistoryLogList());
        return createCorrectVariant(resultList, headers);
    }

    @GET
    @Path("/variable/{varId: [a-zA-Z0-9-:\\._]+}")
    public Response variable_varId(@PathParam("varId") String variableId) {
        Map<String, List<String>> params = getRequestParams(uriInfo);
        String oper = getRelativePath(uriInfo);
        
        List<VariableInstanceLog> varLogList = internalGetVariableInstancesByVarAndValue(variableId, null, params, oper);
        
        List<AuditEvent> results = new ArrayList<AuditEvent>(varLogList);
        JaxbHistoryLogList resultList = paginateAndCreateResult(params, oper, results, new JaxbHistoryLogList());
        
        return createCorrectVariant(resultList, headers);
    }
    
    @GET
    @Path("/variable/{varId: [a-zA-Z0-9-:\\._]+}/value/{value: [a-zA-Z0-9-:\\._]+}")
    public Response variable_varId_value_valueVal(@PathParam("varId") String variableId, @PathParam("value") String value) {
        Map<String, List<String>> params = getRequestParams(uriInfo);
        String oper = getRelativePath(uriInfo);
        List<VariableInstanceLog> varLogList = internalGetVariableInstancesByVarAndValue(variableId, value, params, oper);
        
        List<AuditEvent> results = new ArrayList<AuditEvent>(varLogList);
        JaxbHistoryLogList resultList = paginateAndCreateResult(params, oper, results, new JaxbHistoryLogList());
        
        return createCorrectVariant(resultList, headers);
    } 
   
    @GET
    @Path("/variable/{varId: [a-zA-Z0-9-:\\._]+}/instances")
    // TODO: docs
    public Response variable_varId_instances(@PathParam("varId") String variableId) {
        Map<String, List<String>> params = getRequestParams(uriInfo);
        String oper = getRelativePath(uriInfo);

        // get variables
        List<VariableInstanceLog> varLogList = internalGetVariableInstancesByVarAndValue(variableId, null, params, oper);
        
        // get process instance logs
        int [] pageInfo = getPageNumAndPageSize(params, oper);
        int maxNumResults = getMaxNumResultsNeeded(pageInfo);
        List<ProcessInstanceLog> procInstLogList = getProcessInstanceLogsByVariable(varLogList, maxNumResults);
       
        // paginate
        List<AuditEvent> results = new ArrayList<AuditEvent>(procInstLogList);
        JaxbHistoryLogList resultList = paginateAndCreateResult(pageInfo, results, new JaxbHistoryLogList());
        
        return createCorrectVariant(resultList, headers);
    }
    
    @GET
    @Path("/variable/{varId: [a-zA-Z0-9-:\\.]+}/value/{value: [a-zA-Z0-9-:\\._]+}/instances")
    public Response variable_varId_value_valueVal_instances(@PathParam("varId") String variableId, @PathParam("value") String value) {
        Map<String, List<String>> params = getRequestParams(uriInfo);
        String oper = getRelativePath(uriInfo);

        // get variables
        List<VariableInstanceLog> varLogList = internalGetVariableInstancesByVarAndValue(variableId, value, params, oper);
        
        // get process instance logs
        int [] pageInfo = getPageNumAndPageSize(params, oper);
        int maxNumResults = getMaxNumResultsNeeded(pageInfo);
        List<ProcessInstanceLog> procInstLogList = getProcessInstanceLogsByVariable(varLogList, maxNumResults);
        List<AuditEvent> results = new ArrayList<AuditEvent>(procInstLogList);
        
        JaxbHistoryLogList resultList = paginateAndCreateResult(pageInfo, results, new JaxbHistoryLogList());
        return createCorrectVariant(resultList, headers);
    }
    
    // Helper methods --------------------------------------------------------------------------------------------------------------

    private List<VariableInstanceLog> internalGetVariableInstancesByVarAndValue(String varId, String value, 
            Map<String, List<String>> params, String oper) { 
        // active processes parameter
        String activeProcsParam = getStringParam("activeProcesses", false, params, oper); 
        boolean onlyActiveProcesses = false;
        if( activeProcsParam != null ) { 
            onlyActiveProcesses = Boolean.parseBoolean(activeProcsParam);
        }
       
        Object result;
        if( value == null ) { 
            result = getAuditLogService().findVariableInstancesByName(varId, onlyActiveProcesses);
        } else { 
            result = getAuditLogService().findVariableInstancesByNameAndValue(varId, value, onlyActiveProcesses);
        }
        
        return (List<VariableInstanceLog>) result;
    }

    private List<ProcessInstanceLog> getProcessInstanceLogsByVariable(List<VariableInstanceLog> varLogList, int maxNumResults) {
        int numVarLogs = varLogList.size();
        int numProcInsts = 0;
       
        List<ProcessInstanceLog> resultList = new ArrayList<ProcessInstanceLog>();
        for( int i = 0; i < numVarLogs && numProcInsts < maxNumResults; ++i ) { 
            long procInstId = varLogList.get(i).getProcessInstanceId();
            ProcessInstanceLog procInstlog = getAuditLogService().findProcessInstance(procInstId);
            if( procInstlog != null ) { 
                resultList.add(procInstlog);
                ++numProcInsts;
            }
        }
        return resultList;
    }
}