package com.jarvis.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var codeField: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var btnVerify: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        window.statusBarColor = android.graphics.Color.parseColor("#0B0B0E")

        auth = FirebaseAuth.getInstance()
        emailField = findViewById(R.id.emailField)
        passwordField = findViewById(R.id.passwordField)
        codeField = findViewById(R.id.codeField)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        btnVerify = findViewById(R.id.btnVerify)

        // Если пользователь уже вошел, но почта не подтверждена — показываем поле кода
        val user = auth.currentUser
        if (user != null) {
            if (user.isEmailVerified) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                codeField.visibility = EditText.VISIBLE
                btnVerify.visibility = Button.VISIBLE
            }
        }

        btnLogin.setOnClickListener {
            val email = emailField.text.toString()
            val pass = passwordField.text.toString()
            if (email.isNotEmpty() && pass.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val u = auth.currentUser
                            if (u != null && !u.isEmailVerified) {
                                Toast.makeText(this, "Подтвердите почту! Введите код из письма.", Toast.LENGTH_LONG).show()
                                codeField.visibility = EditText.VISIBLE
                                btnVerify.visibility = Button.VISIBLE
                            } else {
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                        } else {
                            Toast.makeText(this, "Ошибка: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }

        btnRegister.setOnClickListener {
            val email = emailField.text.toString()
            val pass = passwordField.text.toString()
            if (email.isNotEmpty() && pass.isNotEmpty()) {
                auth.createUserWithEmailAndPassword(email, pass)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            auth.currentUser?.sendEmailVerification()
                                ?.addOnCompleteListener {
                                    Toast.makeText(this, "Код отправлен на почту!", Toast.LENGTH_LONG).show()
                                    codeField.visibility = EditText.VISIBLE
                                    btnVerify.visibility = Button.VISIBLE
                                }
                        } else {
                            Toast.makeText(this, "Ошибка: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }

        btnVerify.setOnClickListener {
            // Примечание: Firebase отправляет ссылку, но для кода можно использовать кастомную логику
            // Если ссылка подтверждена, но мы обновляем статус:
            auth.currentUser?.reload()?.addOnCompleteListener {
                if (auth.currentUser?.isEmailVerified == true) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Почта пока не подтверждена. Проверьте ссылку в письме.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
