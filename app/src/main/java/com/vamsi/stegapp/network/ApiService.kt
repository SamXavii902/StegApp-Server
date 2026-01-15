package com.vamsi.stegapp.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class UploadResponse(
    val status: String,
    val url: String,
    val filename: String
)

data class UserRequest(val username: String)
data class RegisterResponse(val status: String, val username: String)
data class CheckUserResponse(val exists: Boolean)

interface ApiService {
    @Multipart
    @POST("upload/")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part
    ): Response<UploadResponse>

    @POST("register")
    suspend fun register(@retrofit2.http.Body request: UserRequest): Response<RegisterResponse>

    @retrofit2.http.GET("check_user/{username}")
    suspend fun checkUser(@retrofit2.http.Path("username") username: String): Response<CheckUserResponse>
}
