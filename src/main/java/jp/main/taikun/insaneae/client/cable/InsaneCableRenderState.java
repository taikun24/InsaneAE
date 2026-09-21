package jp.main.taikun.insaneae.client.cable;

/**
 * AE2 の {@code CableBusRenderState} に「どの Mod のケーブルか」を持たせるための口。
 *
 * <p>AE2 のレンダーステートは (ケーブル種別, 色, チャンネル数) しか持たないので、
 * このままではこちらの 2 本と AE2 のスマートケーブルが区別できない。
 * {@code CableBusRenderStateMixin} がこの口を実装し、
 * <b>{@code equals} / {@code hashCode} にも種類を混ぜる</b>
 * (レンダーステートは板のキャッシュの鍵でもあるため)。</p>
 */
public interface InsaneCableRenderState {

    /** この板を描くケーブルの種類。AE2 のケーブルなら null。 */
    InsaneCableSprites.Kind insaneae$getCableKind();

    void insaneae$setCableKind(InsaneCableSprites.Kind kind);
}
