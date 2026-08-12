import Capacitor

open class GraftViewController: CAPBridgeViewController {
    override open func instanceDescriptor() -> InstanceDescriptor {
        let descriptor = super.instanceDescriptor()
        // Assigned unconditionally so the pointer, not Capacitor's persisted path, is authoritative
        descriptor.appLocation = GraftPointer.resolveActiveBundleDirectory()
        return descriptor
    }
}
