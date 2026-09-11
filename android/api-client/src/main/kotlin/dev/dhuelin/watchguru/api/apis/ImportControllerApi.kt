package dev.dhuelin.watchguru.api.apis

import dev.dhuelin.watchguru.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import dev.dhuelin.watchguru.api.models.CommitImport
import dev.dhuelin.watchguru.api.models.ImportPreviewResponse
import dev.dhuelin.watchguru.api.models.ImportResultResponse
import dev.dhuelin.watchguru.api.models.PreviewImport

interface ImportControllerApi {
    /**
     * POST api/v1/me/imports
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param commitImport 
     * @return [ImportResultResponse]
     */
    @POST("api/v1/me/imports")
    suspend fun commitImport(@Body commitImport: CommitImport): Response<ImportResultResponse>

    /**
     * POST api/v1/me/imports/preview
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param previewImport 
     * @return [ImportPreviewResponse]
     */
    @POST("api/v1/me/imports/preview")
    suspend fun previewImport(@Body previewImport: PreviewImport): Response<ImportPreviewResponse>

}
