import {customElement, html, LitElement, property} from 'lit-element';

import {feather} from '../iconset';

// @ts-ignore
import VerifyStyle from '../style/verify.scss';
// @ts-ignore
import ProfileStyle from '../style/profile.scss';

@customElement('user-profile')
export class Profile extends LitElement {

    @property({type: String})
    private discordId: String | undefined;

    private static discordIdS: String | undefined;

    @property()
    private userInfo: any;

    protected render() {
        this.fetchUserInformation();
        return html`
<header><h6>Profile</h6></header>
<div class="c-centerCard">
    <div class="c-parentCard mdc-card">
        <div class="c-image-cropper"><img src="${this.userInfo.avatarUrl}"></div>
        <h1 class="c-flexcenter">${this.userInfo.discrimName} ${this.userInfo.verified == 2 ? feather.check : ''}</h1>
        <hr>
        <div style="width:fit-content;display: table">
            <div style="display: table-row-group;">
                <h2 class="c-flexcenter">Steam</h2>
                <div class="c-tableRow">
                    <div class="c-tableCell c-tableHeader">Name</div>
                    <div class="c-tableCell c-tableHeader">Steam Link</div>
                    <div class="c-tableCell c-tableHeader">board.portal2.sr Link</div>
                    <div class="c-tableCell c-tableHeader c-center">Rank</div>
                    <div class="c-tableCell c-tableHeader c-center">Remove</div>
                </div>
                ${this.userInfo.steamAccounts.map(this.generateSteamTableRow)}
                <a href="https://luma.portal2.sr/login/steam" class="c-addButton c-flexcenter c-gray mdc-button--raised"><span class="mdc-button__label">Add Steam Account</span></a>
                <h2 class="c-flexcenter">Speedrun.com</h2>
                <div class="c-tableRow">
                    <div class="c-tableCell c-tableHeader">Name</div>
                    <div class="c-tableCell c-tableHeader">Speedrun.com Link</div>
                    <div class="c-tableCell c-tableHeader"></div>
                    <div class="c-tableCell c-tableHeader c-center">Rank</div>
                    <div class="c-tableCell c-tableHeader c-center">Remove</div>
                </div>
                ${this.userInfo.srcomAccounts.map(this.generateSrcomTableRow)}
                <a href="https://luma.portal2.sr/login/srcom" class="c-addButton c-flexcenter c-gold mdc-button--raised"><span class="mdc-button__label">Add Speedrun.com Account</span></a>
                <h2 class="c-flexcenter">Twitch</h2>
                <div class="c-tableRow">
                    <div class="c-tableCell c-tableHeader">Name</div>
                    <div class="c-tableCell c-tableHeader">Twitch Link</div>
                    <div class="c-tableCell c-tableHeader"></div>
                    <div class="c-tableCell c-tableHeader"></div>
                    <div class="c-tableCell c-tableHeader c-center">Remove</div>
                </div>
                ${this.userInfo.twitchAccounts.map(this.generateTwitchTableRow)}
                <a href="https://luma.portal2.sr/login/twitch" class="c-addButton c-flexcenter c-purple mdc-button--raised"><span class="mdc-button__label">Add Twitch Account</span></a>
            </div>
        </div>
    </div>
</div>`
    }
    /*

     */

    protected fetchUserInformation() {
        let request = new XMLHttpRequest();
        console.log(this.discordId);
        request.open('GET', '/user/' + this.discordId, false);
        request.send();
        this.userInfo = JSON.parse(request.response);
    }

    generateSteamTableRow=(account: any)=>{
        return html`<div class="c-tableRow">
                    <div class="c-tableCell">${account.name}</div>
                    <div class="c-tableCell">${account.steamLink != undefined ? html`<a href="${account.steamLink}" target="_blank">Steam  ${feather.externalLink}</a>` : ''}</div>
                    <div class="c-tableCell">${account.iverbLink != undefined ? html`<a href="${account.iverbLink}" target="_blank">board.portal2.sr  ${feather.externalLink}</a>` : ''}</div>
                    <div class="c-tableCell c-center">${account.iverbRank != undefined ? account.iverbRank : ''}</div>
                    <div class="c-tableCell c-center"><button class="mdc-button--raised c-red" @click=${() => { this.removeAccount(account.id)}}><span class="mdc-button__label">X</span></button></div>
                </div>`
    };

    generateSrcomTableRow=(account: any)=>{
        return html`<div class="c-tableRow">
                    <div class="c-tableCell">${account.name}</div>
                    <div class="c-tableCell">${account.link != undefined ? html`<a href="${account.link}" target="_blank">Speedrun.com  ${feather.externalLink}</a>` : ''}</div>
                    <div class="c-tableCell"></div>
                    <div class="c-tableCell c-center">${account.p2Rank != undefined ? account.p2Rank : ''}</div>
                    <div class="c-tableCell c-center"><button class="mdc-button--raised c-red" @click=${() => { this.removeAccount(account.id)}}><span class="mdc-button__label">X</span></button></div>
                </div>`
    };

    generateTwitchTableRow=(account: any)=>{

        console.log(account);

        return html`<div class="c-tableRow">
                    <div class="c-tableCell">${account.name}</div>
                    <div class="c-tableCell">${account.link != undefined ? html`<a href="${account.link}" target="_blank">Twitch  ${feather.externalLink}</a>` : ''}</div>
                    <div class="c-tableCell"></div>
                    <div class="c-tableCell"></div>
                    <div class="c-tableCell c-center"><button class="mdc-button--raised c-red" @click=${() => { this.removeAccount(account.id)}}><span class="mdc-button__label">X</span></button></div>
                </div>`
    };

    removeAccount=(accountId: string)=>{
        //TODO: This is a mess
        fetch('/user/' + Profile.discordIdS + '/connections/' + accountId, {
            method: 'DELETE',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'text/plain'
            }
        }).then(() => window.location.assign('/'));
    };

    // @ts-ignore
    firstUpdated(_changedProperties) {
        Profile.discordIdS = this.discordId;
    }

    static get styles() {
        return [ProfileStyle, VerifyStyle];
    }
}