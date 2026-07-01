package com.rustypastechat.data.api

import com.rustypastechat.data.model.PasteItem
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface RustyPasteApi {
    @Multipart
    @POST("/")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("file") filename: RequestBody? = null,
        @Header("expire") expire: String? = null
    ): Response<ResponseBody>

    @Multipart
    @POST("/")
    suspend fun uploadOneshot(
        @Part file: MultipartBody.Part,
        @Part("oneshot") oneshot: RequestBody,
        @Header("expire") expire: String? = null
    ): Response<ResponseBody>

    @GET("/list")
    suspend fun listFiles(): Response<List<PasteItem>>

    @Streaming
    @GET("/{filename}")
    suspend fun getFile(@Path("filename") filename: String): Response<ResponseBody>

    @GET("/{filename}")
    suspend fun getFileWithDownload(
        @Path("filename") filename: String,
        @Query("download") download: Boolean = true
    ): Response<ResponseBody>

    @DELETE("/{filename}")
    suspend fun deleteFile(@Path("filename") filename: String): Response<ResponseBody>

    @GET("/version")
    suspend fun getVersion(): Response<ResponseBody>
}
