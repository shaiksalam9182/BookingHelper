package com.mine.bookinghelper.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_details")
data class UserDetail(
    @PrimaryKey val id: Int = 1, // Single record, always ID 1
    val name: String = "",
    val age: String = "",
    val gender: String = "",
    val identityType: String = "",
    val identityNumber: String = "",
    val email: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val pincode: String = "",
    val gothram: String = ""
)
