# dlm-demo

# zookeeper-dlm zookeeper 实现分布式锁例子

大致流程:<br>

 * 1、客户端连接zookeeper，并在/lock下创建临时的且有序的子节点，第一个客户端对应的子节点为/lock/lock-0000000000，第二个为/lock/lock-0000000001，以此类推；
 
 * 2、客户端获取/lock下的子节点列表，判断自己创建的子节点是否为当前子节点列表中 序号最小 的子节点，如果是则认为获得锁，否则监听/lock的子节点变更消息，获得子节点变更通知后重复此步骤直至获得锁；
 * 3、执行业务代码；
 * 4、完成业务流程后，删除对应的子节点释放锁。
 * 创建的节点在zookeeper里结构如下所示：<br>
 +/curator<br>
  +--/lock<br>
    +------/_c_2e0e248c-f179-4a04-8e7f-483867fe1403-lock-0000000001<br>
    +------/_c_2e0e248c-f179-4a04-8e7f-483867fe1403-lock-0000000002<br>
    +------/_c_2e0e248c-f179-4a04-8e7f-483867fe1403-lock-0000000003<br>
    +------/_c_2e0e248c-f179-4a04-8e7f-483867fe1403-lock-0000000004

# redis-dlm redis 实现分布式锁例子

setNX()：SET if Not eXists(如果不存在，则 SET),若键 key 已经存在， 则 SETNX 命令不做任何动作,返回值：命令在设置成功时返回 1 ，设置失败时返回 0<br>

getSet()：键 key 的值设为 value ，并返回键 key 在被设置之前的旧的value，返回值：如果键key没有旧值， 也即是说，键 key 在被设置之前并不存在，那么命令返回 nil,当键 key 存在但不是字符串类型时，命令返回一个错误。<br>


<a href="https://blog.csdn.net/dazou1/article/details/88088223">大致流程</a>


