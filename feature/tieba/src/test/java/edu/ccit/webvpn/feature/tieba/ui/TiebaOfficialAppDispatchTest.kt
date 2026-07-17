package edu.ccit.webvpn.feature.tieba.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TiebaOfficialAppDispatchTest {
    @Test
    fun `thread reply uses TiebaLite official client dispatch`() {
        assertEquals(
            "com.baidu.tieba://unidispatch/pb?obj_locate=pb_reply&obj_source=wise&obj_name=index&obj_param2=chrome&has_token=0&qd=scheme&refer=tieba.baidu.com&wise_sample_id=3000232_2-99999_9&fr=bpush&tid=123",
            officialTiebaReplyUri(threadId = 123, postId = null),
        )
    }

    @Test
    fun `floor reply uses TiebaLite official client anchor dispatch`() {
        assertEquals(
            "com.baidu.tieba://unidispatch/pb?obj_locate=comment_lzl_cut_guide&obj_source=wise&obj_name=index&obj_param2=chrome&has_token=0&qd=scheme&refer=tieba.baidu.com&wise_sample_id=3000232_2&hightlight_anchor_pid=456&is_anchor_to_comment=1&comment_sort_type=0&fr=bpush&tid=123",
            officialTiebaReplyUri(threadId = 123, postId = 456),
        )
    }
}
