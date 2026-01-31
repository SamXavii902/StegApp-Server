package com.vamsi.stegapp.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

data class UploadResponse(
    val status: String,
    val url: String,
    val filename: String
)

data class UserRequest(
    val username: String,
    @SerializedName("public_key") val publicKey: String? = null
)

data class KeyUploadRequest(
    val username: String,
    @SerializedName("public_key") val publicKey: String
)

data class KeyFetchResponse(
    val username: String,
    @SerializedName("public_key") val publicKey: String
)

data class RegisterResponse(val status: String, val username: String)
data class CheckUserResponse(val exists: Boolean)

interface ApiService {
    @Multipart
    @POST("upload/")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part
    ): Response<UploadResponse>

    @POST("register")
    suspend fun register(@Body request: UserRequest): Response<RegisterResponse>

    @POST("keys/upload")
    suspend fun uploadKey(@Body request: KeyUploadRequest): Response<Unit>

    @GET("keys/fetch/{username}")
    suspend fun fetchKey(@Path("username") username: String): Response<KeyFetchResponse>

    @GET("check_user/{username}")
    suspend fun checkUser(@Path("username") username: String): Response<CheckUserResponse>
}
