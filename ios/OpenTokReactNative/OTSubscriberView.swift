//
//  OTSubscriberView.swift
//  OpenTokReactNative
//
//  Created by Manik Sachdeva on 1/18/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

import Foundation

@objc(OTSubscriberView)
class OTSubscriberView: UIView {
  @objc var streamId: NSString?
  @objc var fitToView: NSString?
  override init(frame: CGRect) {
    super.init(frame: frame)
  }
  
  required init?(coder aDecoder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }
  
  override func layoutSubviews() {
    if (fitToView! as String) == "fit" {
      OTRN.sharedState.subscribers[streamId! as String]?.viewScaleBehavior = .fit
    } else {
      OTRN.sharedState.subscribers[streamId! as String]?.viewScaleBehavior = .fill
    }
    if let subscriberView = OTRN.sharedState.subscribers[streamId! as String]?.view {
      subscriberView.frame = self.bounds
      subscriberView.layer.cornerRadius = 8
      subscriberView.layer.masksToBounds = true
      addSubview(subscriberView)
    }
  }
}

