import groovy.json.JsonBuilder
import org.bonitasoft.web.extension.rest.RestAPIContext
import org.bonitasoft.web.extension.rest.RestApiController
import org.bonitasoft.web.extension.rest.RestApiResponse
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * This REST API will list the column names declared on the first row of a CSV file.
 * Before calling this API, the file needs to be uploaded and link to a Bonita process instance.
 * This is usually achieved using a file upload widget in a process instantitation form or in a
 * task form and the contract information to set the value of the document declared in the process definition.
 * The storage id of the document need to be provided when calling the REST API.
 * @author Antoine Mottier
 *
 */
class ListCSVFileColumnsName implements RestApiController {

    @Override
    RestApiResponse doHandle(HttpServletRequest request, RestApiResponseBuilder responseBuilder, RestAPIContext context) {
        // Retrieve the document id as HTTP request parameter
        def contentStorageId = request.getParameter "contentStorageId"
        if (contentStorageId == null) {
            return buildResponse(responseBuilder, HttpServletResponse.SC_BAD_REQUEST, """{"error" : "the parameter contentStorageId is missing"}""")
        }

        // Get the document content
        def content = context.apiClient.processAPI.getDocumentContent(contentStorageId)

		// Read the first row of the document
        def is = new ByteArrayInputStream(content)
        def bfReader = new BufferedReader(new InputStreamReader(is))
        String firstRow = bfReader.readLine()
        if(firstRow== null) {
            return buildResponse(responseBuilder, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, """{"error" : "First row of the file is empty"}""")
        }

        // Split the comma separated content of the first row and store it in a List (split returns an Array)
        def columnsName = firstRow.split(',') as List

        // Prepare the result. Result will be a collection with column name and associated index
        def columnsNameAndIndex = columnsName.withIndex().collect { columnName, index ->
            [name: columnName, position: index]
        }
        def result = [columnsNameAndIndex: columnsNameAndIndex]

        // Send the result as a JSON representation
        return buildResponse(responseBuilder, HttpServletResponse.SC_OK, new JsonBuilder(result).toPrettyString())
    }

    /**
     * Build an HTTP response.
     *
     * @param responseBuilder the Rest API response builder
     * @param httpStatus the status of the response
     * @param body the response body
     * @return a RestAPIResponse
     */
    RestApiResponse buildResponse(RestApiResponseBuilder responseBuilder, int httpStatus, Serializable body) {
        return responseBuilder.with {
            withResponseStatus(httpStatus)
            withResponse(body)
            build()
        }
    }

    /**
     * Returns a paged result like Bonita BPM REST APIs.
     * Build a response with a content-range.
     *
     * @param responseBuilder the Rest API response builder
     * @param body the response body
     * @param p the page index
     * @param c the number of result per page
     * @param total the total number of results
     * @return a RestAPIResponse
     */
    RestApiResponse buildPagedResponse(RestApiResponseBuilder responseBuilder, Serializable body, int p, int c, long total) {
        return responseBuilder.with {
            withContentRange(p, c, total)
            withResponse(body)
            build()
        }
    }

}
