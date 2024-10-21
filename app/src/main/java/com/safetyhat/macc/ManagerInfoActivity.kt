package com.safetyhat.macc
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class ManagerInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_info)

        val firstName = "John"
        val lastName = "Doe"
        val cf = "JDOE123456789"
        val birthday = "01/01/1990"
        val phoneNumber = "+39 123 456 7890"

        findViewById<TextView>(R.id.first_name_text).text = firstName
        findViewById<TextView>(R.id.last_name_text).text = lastName
        findViewById<TextView>(R.id.cf_text).text = cf
        findViewById<TextView>(R.id.birthday_text).text = birthday
        findViewById<TextView>(R.id.telephone_text).text = phoneNumber
    }
}
