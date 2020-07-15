package com.development.app.callback

import com.development.app.Model.ShippingOrderModel

interface IShippingCallbackListner {
    fun onShippingOrderLoadSuccess(shippingOrders:List<ShippingOrderModel>)
    fun onShippingOrderLoadFailed(message:String)
}