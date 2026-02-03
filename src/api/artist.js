import request from '@/utils/request';
import { mapTrackPlayableStatus } from '@/utils/common';
import { isAccountLoggedIn } from '@/utils/auth';
import { getTrackDetail } from '@/api/track';


/**
 * 收藏歌手
 * 说明 : 调用此接口 , 传入歌手 id, 可收藏歌手
 * - id: 歌手 id
 * - t: 操作,1 为收藏,其他为取消收藏
 * @param {Object} params
 * @param {number} params.id
 * @param {number} params.t
 */
export function followAArtist(params) {
  return request({
    url: '/artist/sub',
    method: 'post',
    params,
  });
}

/**
 * 相似歌手
 * 说明 : 调用此接口 , 传入歌手 id, 可获得相似歌手
 * - id: 歌手 id
 * @param {number} id
 */
export function similarArtists(id) {
  return request({
    url: '/simi/artist',
    method: 'post',
    params: { id },
  });
}
