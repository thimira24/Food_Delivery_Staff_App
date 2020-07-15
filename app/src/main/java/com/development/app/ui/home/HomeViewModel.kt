package com.development.app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.development.app.Common.Common
import com.development.app.Model.ShippingOrderModel
import com.development.app.callback.IShippingCallbackListner
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class HomeViewModel : ViewModel(), IShippingCallbackListner {

    private val orderModelMutableLiveData:MutableLiveData<List<ShippingOrderModel>>
    val messageError:MutableLiveData<String>
    private val listner: IShippingCallbackListner

    init {
        orderModelMutableLiveData = MutableLiveData()
        messageError = MutableLiveData()
        listner = this
    }

    fun getOrderModelMutableLiveData(shipperPhone:String):MutableLiveData<List<ShippingOrderModel>>{
        loadOrderByShipper(shipperPhone)
        return orderModelMutableLiveData
    }

    private fun loadOrderByShipper(shipperPhone: String) {
        val tempList:MutableList<ShippingOrderModel> = ArrayList()
        val orderRef = FirebaseDatabase.getInstance()
            .getReference(Common.SHIPPING_ORDER_REF)
            .orderByChild("shipperPhone")
            .equalTo(Common.currentShipperUser!!.phone)

        orderRef.addListenerForSingleValueEvent(object:ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {
                listner.onShippingOrderLoadFailed(p0.message)
            }

            override fun onDataChange(p0: DataSnapshot) {
               for (itemSnapShot in p0.children)
               {
                   val shippingOrder = itemSnapShot.getValue(ShippingOrderModel::class.java!!)
                   shippingOrder!!.key = itemSnapShot.key
                   tempList.add(shippingOrder!!)
               }
                listner.onShippingOrderLoadSuccess(tempList)
            }

        })
    }

    override fun onShippingOrderLoadSuccess(shippingOrders: List<ShippingOrderModel>) {
        orderModelMutableLiveData.value = shippingOrders
    }

    override fun onShippingOrderLoadFailed(message: String) {
       messageError.value = message
    }

}