import org.bonitasoft.engine.bpm.document.Document
import org.bonitasoft.engine.bpm.document.DocumentAttachmentException
import org.bonitasoft.engine.bpm.document.DocumentNotFoundException
import org.bonitasoft.engine.bpm.document.DocumentValue
import org.bonitasoft.web.extension.rest.RestAPIContext
import org.bonitasoft.web.extension.rest.RestApiController
import org.bonitasoft.web.extension.rest.RestApiResponse
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * This REST API extension will create a new CSV document by removing some columns of an existing one.
 * Input document and generated document are stored in Bonita embedded document management service.
 * Before calling this API the input document must have been uploaded.
 * @author Antoine Mottier
 *
 */
class GenerateCSVFile implements RestApiController {

	/** Logger for debugging */
    private static final Logger LOGGER = LoggerFactory.getLogger('org.bonitasoft.customrestapi')

    @Override
    RestApiResponse doHandle(HttpServletRequest request, RestApiResponseBuilder responseBuilder, RestAPIContext context) {
        // Retrieve the document content storage id from the HTTP GET request parameter
		// From a task form, the contentStorageId is available using the provided "context" form variable.
        def contentStorageId = request.getParameter "contentStorageId"

        // Content storage id is mandatory to get the file content. Returns HTTP error code 400 if parameter is missing
        if (contentStorageId == null) {
            return buildResponse(responseBuilder, HttpServletResponse.SC_BAD_REQUEST, """{"error" : "the parameter contentStorageId is missing"}""")
        }

		// We need to retrieve process instance and process definition information in order to generate the new
		// document download link. We ask the user to provide the processInstanceId when calling the API.
		// In a task form processInstanceId should be available from the "context" form variable.
        def processInstanceId = request.getParameter "processInstanceId"

        if (processInstanceId == null) {
            return buildResponse(responseBuilder, HttpServletResponse.SC_BAD_REQUEST, """{"error" : "the parameter processInstanceId is missing"}""")
        }

        // Retrieve the index of the columns to remove in the new file from the "columnIndex" parameter provide in the HTTP GET request URL.
        // Parameter "columnIndex" can be includes several times to remove several columns in the generated document.
		// getParameterValues returns an array of String that we convert to a List for further processing.
        def columnsIndexAsString = (request.getParameterValues("columnIndex")) as List

		// We need to convert from parameter String value to int for columns index.
        def columnsIndex = columnsIndexAsString.collect { it as int}
		// We want to have the last index first in order to avoid issue while removing column.
        columnsIndex.sort { -it }

        LOGGER.debug("Columns index: {}", columnsIndex)

        // Get the input document content using Bonita Engine API
        def content = context.apiClient.processAPI.getDocumentContent(contentStorageId)

        // Buffered writer for the new file
        def os = new ByteArrayOutputStream()
        def writer = new BufferedWriter(new OutputStreamWriter(os))

		// Buffered reader for the input file content
        def is = new ByteArrayInputStream(content)
        def bfReader = new BufferedReader(new InputStreamReader(is))
		
		// Current row in the input document
        def row
		
        // For each row
        while((row = bfReader.readLine()) != null) {
            // Split the row for each columns and put the values in a list
            def columnsValue = row.split(',') as List
            LOGGER.debug("Columns value: ${columnsValue}")
			
            // For each index of columns to remove takes the value out of the list
            columnsIndex.each {
                LOGGER.debug("Trying to remove element at ${it}")
                columnsValue.removeAt(it)
            }

            LOGGER.debug("Columns value after remove: ${columnsValue}")
			
			// Write the new line content in the output file
            writer.write(columnsValue.join(','))
            writer.write('\n')
        }

		// Flush the buffer
        writer.flush()

        def document

        // The document might already exists due to a previous API call.
        try {
            // If we call the API for the first time we attach a new document (attachDocument method).
            // This API call create a new version of the output document.
            // Document must be named in the process definition: generatedCSVFile
            // The name of the generated file (output.csv in this example) can be customized
            document = context.apiClient.processAPI.attachDocument(processInstanceId as long, 'generatedCSVFile', 'output.csv', 'text/csv', os.toByteArray())
        } catch(DocumentAttachmentException e) {
            // If it is not the first call to the API, a version of the document already exists and we attach a new version of the document (attachNewDocumentVersion method).
            document = context.apiClient.processAPI.attachNewDocumentVersion(processInstanceId as long, 'generatedCSVFile', 'output.csv', 'text/csv', os.toByteArray())
        }

		// To build the URL to download the document we need the process definition name and version.
		// First get process instance information using the process instance id received as REST API extension parameter.
        def processInstance = context.apiClient.processAPI.getProcessInstance(processInstanceId as long)
		// Use process definition id stored in process instance information to get all the information about the process definition including name and version 
        def processDefinition = context.apiClient.processAPI.getProcessDefinition(processInstance.processDefinitionId)

		// The response of the REST API call will be a temporary redirection (HTTP code 307) to the download URL for the document.
        responseBuilder.withResponseStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT).
                withAdditionalHeader('Location', "/bonita/portal/resource/processInstance/${processDefinition.name}/${processDefinition.version}/API/documentDownload?fileName=output.csv&contentStorageId=${document.contentStorageId}").
                build()
    }

    /**
     * Build an HTTP response.
     *
     * @param responseBuilder the Rest API response builder
     * @param httpStatus the status of the response
     * @param body the response body
     * @return a RestAPIResponse
     */
    static RestApiResponse buildResponse(RestApiResponseBuilder responseBuilder, int httpStatus, Serializable body) {
        return responseBuilder.with {
            withResponseStatus(httpStatus)
            withResponse(body)
            build()
        }
    }

}
