package com.development.app.adapter

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.development.app.Common.Common
import com.development.app.Model.ShippingOrderModel
import com.development.app.R
import com.development.app.ShippingActivity
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import io.paperdb.Paper
import java.text.SimpleDateFormat

class MyShippingOrderAdapter (var context:Context,
                              var shippingOrderModelList: List<ShippingOrderModel>) : RecyclerView.Adapter<MyShippingOrderAdapter.MyViewHolder>() {

    var simpleDateFormat:SimpleDateFormat

    init {
        simpleDateFormat = SimpleDateFormat("dd-MM-yyyy, HH:mm")
        Paper.init(context)
    }

    inner class MyViewHolder(itemView:View):RecyclerView.ViewHolder(itemView)
    {
        var txt_date:TextView
        var txt_order_address:TextView
        var txt_order_number:TextView
        var txt_payment:TextView

        var txt_name:TextView
        var txt_price:TextView
        var txt_phone:TextView

        var img_food:ImageView
        var btn_shop_now:MaterialButton

        init {
            txt_date = itemView.findViewById(R.id.txt_date) as TextView
            txt_order_address = itemView.findViewById(R.id.txt_order_address) as TextView
            txt_order_number = itemView.findViewById(R.id.txt_order_number) as TextView
            txt_payment = itemView.findViewById(R.id.txt_payment) as TextView
            img_food = itemView.findViewById(R.id.img_food) as ImageView
            btn_shop_now = itemView.findViewById(R.id.btn_ship_now) as MaterialButton

            txt_name = itemView.findViewById(R.id.txt_customer) as TextView
            txt_price = itemView.findViewById(R.id.txt_total) as TextView
            txt_phone = itemView.findViewById(R.id.txt_phone) as TextView

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
       val itemView = LayoutInflater.from(context).inflate(R.layout.layout_shipping_order, parent, false)
        return MyViewHolder(itemView)
    }

    override fun getItemCount(): Int {
        return shippingOrderModelList.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {

        Glide.with(context).load(shippingOrderModelList.get(position).orderModel!!.cartItemList!![0].foodImage)

        holder.txt_date!!.text = StringBuilder(simpleDateFormat.format(shippingOrderModelList[position].orderModel!!.createDate))

        Common.setSpanStringColor("No : ", shippingOrderModelList[position].orderModel!!.key,
            holder.txt_order_number, Color.parseColor("#FFFFFF"))

        Common.setSpanStringColor("Address : ", shippingOrderModelList[position].orderModel!!.shippingAddress,
            holder.txt_order_address, Color.parseColor("#FFFFFF"))

        Common.setSpanStringColor("Payment: ", shippingOrderModelList[position].orderModel!!.transactionId,
            holder.txt_payment, Color.parseColor("#FFFFFF"))

        Common.setSpanStringColor("Name : ", shippingOrderModelList[position].orderModel!!.userName,
            holder.txt_name, Color.parseColor("#FFFFFF"))

        Common.setSpanStringColor("Rs ", shippingOrderModelList[position].orderModel!!.totalPayment.toString(),
            holder.txt_price, Color.parseColor("#FFFFFF"))

        Common.setSpanStringColor("Phone : ", shippingOrderModelList[position].orderModel!!.userPhone,
            holder.txt_phone, Color.parseColor("#FFFFFF"))

        if (shippingOrderModelList[position].isStartTrip)
        {
            holder.btn_shop_now.isEnabled = false
        }
        // event
        holder.btn_shop_now.setOnClickListener {
            //write data
            Paper.book().write(Common.SHIPPING_DATA, Gson().toJson(shippingOrderModelList[0]))
            context.startActivity(Intent(context, ShippingActivity::class.java))
        }
    }
}