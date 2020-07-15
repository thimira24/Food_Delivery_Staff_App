package com.development.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.development.app.Common.Common
import com.development.app.Model.ShipperUserModel
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import dmax.dialog.SpotsDialog
import io.paperdb.Paper
import kotlinx.android.synthetic.main.layout_register.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private var firebaseAuth: FirebaseAuth? = null
    private var listener: FirebaseAuth.AuthStateListener? = null
    private var dialog: AlertDialog? = null
    private var serverRef: DatabaseReference? = null
    private var providers: List<AuthUI.IdpConfig>? = null

    companion object {
        private val APP_REQUEST_CODE = 7171
    }

    override fun onStart() {
        super.onStart()
        firebaseAuth!!.addAuthStateListener(listener!!)
    }

    override fun onStop() {
        firebaseAuth!!.removeAuthStateListener(listener!!)
        super.onStop()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)
        init()

        //Delete data
        Paper.init(this)
//        Paper.book().delete(Common.TRIP_START)
//        Paper.book().delete(Common.SHIPPING_DATA)
    }

    private fun init() {

        providers = Arrays.asList<AuthUI.IdpConfig>(AuthUI.IdpConfig.PhoneBuilder().build())
        serverRef = FirebaseDatabase.getInstance().getReference(Common.SHIPPER_REF)
        firebaseAuth = FirebaseAuth.getInstance()
        dialog = SpotsDialog.Builder().setContext(this).setCancelable(false).build()
        listener = object : FirebaseAuth.AuthStateListener {
            override fun onAuthStateChanged(firebaseAuth: FirebaseAuth) {

                val user = firebaseAuth.currentUser
                if (user != null)
                {
                    checkServerUserFromFirebase(user)
                } else
                {
                    phoneLogin()
                }
            }
        }
    }

    private fun checkServerUserFromFirebase(user: FirebaseUser) {
        dialog!!.show()
        serverRef!!.child(user.uid)
            .addListenerForSingleValueEvent(object: ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    dialog!!.dismiss()
                    Toast.makeText(this@MainActivity, ""+p0.message, Toast.LENGTH_SHORT).show()
                }

                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists())
                    {
                        val userModel = dataSnapshot.getValue(ShipperUserModel::class.java)
                        if (userModel!!.isActive)
                        {
                            goToHomeActivity(userModel)
                        }
                        else
                        {
                            dialog!!.dismiss()
                            // Toast.makeText(this@MainActivity,"Account is not Activated", Toast.LENGTH_LONG).show()
                            message()
                        }
                    }
                    else
                    {
                        dialog!!.dismiss()
                        showRegisterDialog(user)
                    }
                }

            })
    }

    private fun goToHomeActivity(userModel: ShipperUserModel) {
        dialog!!.dismiss()
        Common.currentShipperUser = userModel
        startActivity(Intent(this, HomeActivity::class.java))
        Toast.makeText(this,"Account is Activated", Toast.LENGTH_SHORT).show()
        finish()

    }

    private fun message() {

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val  itemView = LayoutInflater.from(this).inflate(R.layout.layout_activation_message, null)
        val btn_Activation = itemView.findViewById<View>(R.id.btn_activation) as Button

        builder.setView(itemView)
        val shows = builder.create()

        btn_Activation.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        shows.show()
    }

    private fun showRegisterDialog(user: FirebaseUser) {


        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val  itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null)
        val edt_name = itemView.findViewById<View>(R.id.edt_name) as EditText
        val edt_phone = itemView.findViewById<View>(R.id.edt_phone) as EditText
        val edt_email = itemView.findViewById<View>(R.id.edt_email) as EditText
        val edt_nic = itemView.findViewById<View>(R.id.edt_nic) as EditText
        val edt_address = itemView.findViewById<View>(R.id.edt_address) as EditText

        val edt_vehicle = itemView.findViewById<View>(R.id.edt_vehicle) as EditText
        val edt_vehicle_no = itemView.findViewById<View>(R.id.edt_vehicle_no) as EditText
        val edt_area = itemView.findViewById<View>(R.id.edt_area) as EditText

        // sign up button and dissmiss button
        val btn_register = itemView.findViewById<View>(R.id.btn_signup) as Button
        val btn_dissmiss = itemView.findViewById<View>(R.id.txt_dismiss) as TextView

        //set automatically phone number in registration form
        edt_phone.setText(user.phoneNumber)

        // exit from registration
        btn_dissmiss.setOnClickListener { System.exit(-1) }

        builder.setView(itemView)
        val registerDialog = builder.create()

        // register button
        btn_register.setOnClickListener {

            if (TextUtils.isEmpty(edt_name.text))
            {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val shipperUserModel = ShipperUserModel()
            shipperUserModel.uid = user.uid
            shipperUserModel.name = edt_name.text.toString()
            shipperUserModel.phone = edt_phone.text.toString()
            shipperUserModel.email = edt_email.text.toString()
            shipperUserModel.nic = edt_nic.text.toString()
            shipperUserModel.address = edt_address.text.toString()
            shipperUserModel.vehicle = edt_vehicle.text.toString()
            shipperUserModel.vehicle_no = edt_vehicle_no.text.toString()
            shipperUserModel.area = edt_area.text.toString()
            shipperUserModel.isActive = false

            dialog!!.show()
            serverRef!!.child(shipperUserModel.uid!!)
                .setValue(shipperUserModel)
                .addOnCompleteListener { e ->
                    dialog!!.dismiss()
                    //Toast.makeText(this, "", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener { _ ->
                    dialog!!.dismiss()
                    //Toast.makeText(this, "Register success! Admin will activate your account.", Toast.LENGTH_SHORT).show()
                    message()
                    registerDialog.dismiss()
                }
           // registerDialog.dismiss()
        }

        registerDialog.show()
    }

    private fun phoneLogin() {
        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers!!)
                .build(), APP_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == APP_REQUEST_CODE)
        {

            if (resultCode == Activity.RESULT_OK)
            {
                val  user = FirebaseAuth.getInstance().currentUser
            }
            else
            {
                Toast.makeText(this,"Try Again", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
